package base.api.feature.posorder.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.posorder.dto.response.RefundResponse;
import base.api.feature.posorder.repository.OrderItemRepository;
import base.api.feature.posorder.repository.OrderRefundRepository;
import base.api.feature.posorder.repository.OrderRepository;
import base.api.feature.posorder.service.RefundService;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.shared.entity.OrderItemModel;
import base.api.shared.entity.OrderModel;
import base.api.shared.entity.OrderRefundModel;
import base.api.shared.entity.PointTransactionModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BusinessException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class RefundServiceImpl implements RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundServiceImpl.class);

    /** Cửa sổ cho phép xin hoàn/trả tính từ lúc tạo đơn — chặn ở server, không tin client. */
    private static final long REFUND_WINDOW_MINUTES = 5;

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    @Autowired
    private OrderRefundRepository orderRefundRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private BranchInventoryRepository branchInventoryRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    // =========================================================================
    // Cashier yêu cầu hoàn/trả
    // =========================================================================

    @Override
    @Transactional
    public RefundResponse requestRefund(Long orderId, String reason) {
        UserModel cashier = requireCashier();
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("A reason is required to request a refund.");
        }

        OrderModel order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found."));
        if (!Objects.equals(order.getBranchId(), cashier.getBranchId())) {
            throw new BusinessException("This order belongs to another branch.");
        }
        if (!"COMPLETED".equals(order.getStatus())) {
            throw new BusinessException("Only completed orders can be refunded.");
        }

        // Cửa sổ 5 phút: quá hạn thì không cho xin nữa (kiểm bằng giờ server).
        if (order.getCreatedAt() == null
                || order.getCreatedAt().plusMinutes(REFUND_WINDOW_MINUTES).isBefore(LocalDateTime.now())) {
            throw new BusinessException("The 5-minute refund window for this order has passed.");
        }

        // Không cho xin trùng khi đơn đã có yêu cầu chờ duyệt hoặc đã được duyệt.
        if (orderRefundRepository.existsByOrderIdAndStatusIn(
                orderId, List.of(STATUS_PENDING, STATUS_APPROVED))) {
            throw new BusinessException("A refund request for this order is already in progress.");
        }

        List<OrderItemModel> items = orderItemRepository.findByOrderIdIn(List.of(order.getId()));
        assertAllItemsRefundable(items);

        LocalDateTime now = LocalDateTime.now();
        OrderRefundModel refund = new OrderRefundModel();
        refund.setOrderId(orderId);
        refund.setBranchId(order.getBranchId());
        refund.setRequestedBy(cashier.getId());
        refund.setReason(reason.trim());
        refund.setCreatedAt(now);

        return completeRefund(
                refund,
                order,
                items,
                cashier.getId(),
                "Processed at POS without manager approval.");
    }

    // =========================================================================
    // BM duyệt
    // =========================================================================

    @Override
    public List<RefundResponse> getPendingRefunds() {
        UserModel manager = requireBranchManager();
        return orderRefundRepository
                .findByBranchIdAndStatusOrderByCreatedAtDesc(manager.getBranchId(), STATUS_PENDING)
                .stream()
                .map(refund -> toResponse(
                        refund,
                        orderRepository.findById(refund.getOrderId()).orElse(null)))
                .toList();
    }

    @Override
    @Transactional
    public RefundResponse approveRefund(Long refundId, String note) {
        UserModel manager = requireBranchManager();
        OrderRefundModel refund = requirePendingInBranch(refundId, manager);

        OrderModel order = orderRepository.findById(refund.getOrderId())
                .orElseThrow(() -> new NotFoundException("Order not found."));

        // 1. Hoàn tồn kho từng dòng hàng của đơn.
        List<OrderItemModel> items = orderItemRepository.findByOrderIdIn(List.of(order.getId()));
        assertAllItemsRefundable(items);
        for (OrderItemModel item : items) {
            branchInventoryRepository.addStock(
                    order.getBranchId(), item.getProductId(), item.getQuantity());
        }

        // 2. Thu hồi điểm — best-effort, KHÔNG làm fail refund. Các query atomic chỉ trả
        //    về số row update (0 khi khách đã tiêu hết điểm tặng) chứ không ném lỗi.
        if (order.getCustomerId() != null) {
            long earned = order.getPointsEarned() == null ? 0L : order.getPointsEarned();
            if (earned > 0) {
                // Trừ lại điểm đã tặng; bỏ qua nếu khách không còn đủ (query khớp 0 row).
                userRepository.deductPointsAtomic(order.getCustomerId(), earned);
            }
            long redeemed = order.getPointsRedeemed() == null ? 0L : order.getPointsRedeemed();
            if (redeemed > 0) {
                // Hoàn lại điểm khách đã dùng để đổi trong đơn.
                userRepository.refundPointsAtomic(order.getCustomerId(), redeemed);
            }
            // Ghi lịch sử đảo điểm cho báo cáo — best-effort, KHÔNG làm fail refund.
            // Điểm tặng bị thu hồi ghi âm; điểm đổi hoàn lại ghi dương.
            recordReversalHistory(order.getCustomerId(), order.getId(), earned, redeemed);
        }

        // 3. Đơn chuyển REFUNDED để loại khỏi doanh thu.
        order.setStatus("REFUNDED");
        orderRepository.save(order);

        refund.setStatus(STATUS_APPROVED);
        refund.setReviewedBy(manager.getId());
        refund.setReviewedAt(LocalDateTime.now());
        refund.setReviewNote(note != null ? note.trim() : null);
        orderRefundRepository.save(refund);

        return toResponse(refund, order);
    }

    @Override
    @Transactional
    public RefundResponse rejectRefund(Long refundId, String note) {
        UserModel manager = requireBranchManager();
        if (note == null || note.isBlank()) {
            throw new BusinessException("A note is required to reject a refund.");
        }
        OrderRefundModel refund = requirePendingInBranch(refundId, manager);

        // Từ chối: đơn giữ nguyên COMPLETED, chỉ đóng yêu cầu.
        refund.setStatus(STATUS_REJECTED);
        refund.setReviewedBy(manager.getId());
        refund.setReviewedAt(LocalDateTime.now());
        refund.setReviewNote(note.trim());
        orderRefundRepository.save(refund);

        return toResponse(refund, orderRepository.findById(refund.getOrderId()).orElse(null));
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Ghi lịch sử đảo điểm khi duyệt hoàn đơn. Best-effort: nuốt mọi lỗi để không làm
     * fail refund (điểm thực đã được thu hồi/hoàn ở trên; đây chỉ là log cho báo cáo).
     */
    private void assertAllItemsRefundable(List<OrderItemModel> items) {
        items.stream()
                .filter(item -> Boolean.FALSE.equals(item.getRefundable()))
                .findFirst()
                .ifPresent(item -> {
                    String name = item.getProductName() == null ? "This product" : item.getProductName();
                    throw new BusinessException(name + " is non-refundable.");
                });
    }

    private RefundResponse completeRefund(
            OrderRefundModel refund,
            OrderModel order,
            List<OrderItemModel> items,
            Long reviewerId,
            String note) {
        for (OrderItemModel item : items) {
            branchInventoryRepository.addStock(
                    order.getBranchId(), item.getProductId(), item.getQuantity());
        }

        if (order.getCustomerId() != null) {
            long earned = order.getPointsEarned() == null ? 0L : order.getPointsEarned();
            if (earned > 0) {
                userRepository.deductPointsAtomic(order.getCustomerId(), earned);
            }
            long redeemed = order.getPointsRedeemed() == null ? 0L : order.getPointsRedeemed();
            if (redeemed > 0) {
                userRepository.refundPointsAtomic(order.getCustomerId(), redeemed);
            }
            recordReversalHistory(order.getCustomerId(), order.getId(), earned, redeemed);
        }

        order.setStatus("REFUNDED");
        orderRepository.save(order);

        refund.setStatus(STATUS_APPROVED);
        refund.setReviewedBy(reviewerId);
        refund.setReviewedAt(LocalDateTime.now());
        refund.setReviewNote(note != null && !note.isBlank() ? note.trim() : null);
        OrderRefundModel saved = orderRefundRepository.save(refund);
        return toResponse(saved, order);
    }

    private void recordReversalHistory(Long customerId, Long orderId, long earned, long redeemed) {
        try {
            if (earned > 0) {
                savePointTransaction(customerId, orderId, -earned);
            }
            if (redeemed > 0) {
                savePointTransaction(customerId, orderId, redeemed);
            }
        } catch (Exception ex) {
            log.warn("Failed to record point reversal history for order {}: {}", orderId, ex.getMessage());
        }
    }

    private void savePointTransaction(Long customerId, Long orderId, long points) {
        PointTransactionModel transaction = new PointTransactionModel();
        transaction.setCustomerId(customerId);
        transaction.setOrderId(orderId);
        transaction.setPoints(points);
        transaction.setType("REFUND_REVERSAL");
        transaction.setCreatedAt(LocalDateTime.now());
        pointTransactionRepository.save(transaction);
    }

    private OrderRefundModel requirePendingInBranch(Long refundId, UserModel manager) {
        OrderRefundModel refund = orderRefundRepository.findById(refundId)
                .orElseThrow(() -> new NotFoundException("Refund request not found."));
        if (!STATUS_PENDING.equals(refund.getStatus())) {
            throw new BusinessException("Only pending refunds can be reviewed.");
        }
        if (!Objects.equals(refund.getBranchId(), manager.getBranchId())) {
            throw new BusinessException("You can only review refunds in your own branch.");
        }
        return refund;
    }

    private RefundResponse toResponse(OrderRefundModel refund, OrderModel order) {
        RefundResponse response = new RefundResponse();
        response.setRefundId(refund.getId());
        response.setOrderId(refund.getOrderId());
        response.setReason(refund.getReason());
        response.setStatus(refund.getStatus());
        response.setReviewNote(refund.getReviewNote());
        response.setCreatedAt(refund.getCreatedAt());
        response.setReviewedAt(refund.getReviewedAt());
        if (order != null) {
            response.setInvoiceCode(order.getInvoiceCode());
            response.setOrderTotal(order.getTotal());
        }
        if (refund.getRequestedBy() != null) {
            userRepository.findById(refund.getRequestedBy())
                    .ifPresent(u -> response.setRequestedByName(formatName(u)));
        }
        if (refund.getReviewedBy() != null) {
            userRepository.findById(refund.getReviewedBy())
                    .ifPresent(u -> response.setReviewedByName(formatName(u)));
        }
        return response;
    }

    private UserModel requireCashier() {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole role = currentUserProvider.getCurrentUserRole();
        if (role != UserRole.CASHIER) {
            throw new BusinessException("Only cashiers can request a refund.");
        }
        if (currentUser.getBranchId() == null) {
            throw new BusinessException("Cashier is not assigned to a branch.");
        }
        return currentUser;
    }

    private UserModel requireBranchManager() {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole role = currentUserProvider.getCurrentUserRole();
        if (role != UserRole.BRANCH_MANAGER) {
            throw new BusinessException("Only branch managers can review refunds.");
        }
        if (currentUser.getBranchId() == null) {
            throw new BusinessException("Branch manager is not assigned to a branch.");
        }
        return currentUser;
    }

    private String formatName(UserModel user) {
        String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String last = user.getLastName() != null ? user.getLastName().trim() : "";
        String combined = (first + " " + last).trim();
        return combined.isEmpty() ? user.getUserName() : combined;
    }
}
