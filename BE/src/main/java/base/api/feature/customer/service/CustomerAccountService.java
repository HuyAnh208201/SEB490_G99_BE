package base.api.feature.customer.service;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.customer.dto.CustomerDtos;
import base.api.feature.posorder.repository.OrderRepository;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.shared.entity.MembershipTierModel;
import base.api.shared.entity.OrderModel;
import base.api.shared.entity.PointTransactionModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserGender;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CustomerAccountService {

    private final IUserRepository userRepository;
    private final PointTransactionRepository pointRepository;
    private final OrderRepository orderRepository;
    private final CustomerTierService tierService;

    @Value("${loyalty.vnd-per-point:10000}")
    private long vndPerPoint;
    @Value("${loyalty.point-value-vnd:1000}")
    private long pointValueVnd;

    public CustomerAccountService(
            IUserRepository userRepository,
            PointTransactionRepository pointRepository,
            OrderRepository orderRepository,
            CustomerTierService tierService) {
        this.userRepository = userRepository;
        this.pointRepository = pointRepository;
        this.orderRepository = orderRepository;
        this.tierService = tierService;
    }

    @Transactional
    public CustomerDtos.ProfileResponse getProfile(UserModel currentUser) {
        UserModel user = requireUser(currentUser.getId());
        return toProfile(user, tierService.syncUserTier(user));
    }

    @Transactional
    public CustomerDtos.ProfileResponse updateProfile(
            UserModel currentUser,
            CustomerDtos.UpdateProfileRequest request) {
        UserModel user = requireUser(currentUser.getId());
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getDateOfBirth() != null) {
            user.setBirthDate(request.getDateOfBirth().atStartOfDay());
        }
        if (request.getGender() != null && !request.getGender().isBlank()) {
            try {
                user.setGender(UserGender.valueOf(request.getGender().trim().toUpperCase()));
            } catch (IllegalArgumentException exception) {
                throw new BadRequestException("Invalid gender");
            }
        }
        userRepository.save(user);
        return toProfile(user, tierService.syncUserTier(user));
    }

    @Transactional(readOnly = true)
    public CustomerDtos.PageResponse<CustomerDtos.PointHistoryItem> pointHistory(
            Long userId,
            String requestedType,
            int requestedPage,
            int requestedSize) {
        int page = Math.max(requestedPage, 0);
        int size = Math.min(Math.max(requestedSize, 1), 50);
        String type = requestedType == null ? "ALL" : requestedType.trim().toUpperCase();
        if ("EARN".equals(type) || "REDEEM".equals(type)) {
            type = "INVOICE";
        }

        List<CustomerDtos.PointHistoryItem> items = new ArrayList<>();
        if ("ALL".equals(type) || "INVOICE".equals(type)) {
            Page<OrderModel> orders = orderRepository.findByCustomerIdOrderByCreatedAtDesc(
                    userId, PageRequest.of(0, 200));
            orders.forEach(order -> items.add(toInvoiceHistory(order)));
        }
        if ("ALL".equals(type) || "REFUND".equals(type) || "REFUND_REVERSAL".equals(type)) {
            Page<PointTransactionModel> refunds =
                    pointRepository.findByCustomerIdAndTypeIgnoreCaseOrderByCreatedAtDesc(
                            userId, "REFUND_REVERSAL", PageRequest.of(0, 200));
            refunds.forEach(refund -> items.add(toRefundHistory(refund)));
        }
        items.sort(Comparator.comparing(
                CustomerDtos.PointHistoryItem::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        int from = Math.min(page * size, items.size());
        int to = Math.min(from + size, items.size());
        CustomerDtos.PageResponse<CustomerDtos.PointHistoryItem> response = new CustomerDtos.PageResponse<>();
        response.setContent(List.copyOf(items.subList(from, to)));
        response.setPage(page);
        response.setSize(size);
        response.setTotalElements(items.size());
        response.setTotalPages((int) Math.ceil(items.size() / (double) size));
        return response;
    }

    public CustomerDtos.LoyaltyConfigResponse loyaltyConfig() {
        CustomerDtos.LoyaltyConfigResponse response = new CustomerDtos.LoyaltyConfigResponse();
        response.setVndPerPoint(vndPerPoint);
        response.setPointValueVnd(pointValueVnd);
        return response;
    }

    @Transactional
    public List<CustomerDtos.TierResponse> listTiers(UserModel user) {
        MembershipTierModel current = tierService.syncUserTier(requireUser(user.getId()));
        Long currentId = current == null ? null : current.getId();
        return tierService.allActive().stream().map(tier -> toTier(tier, currentId)).toList();
    }

    public CustomerDtos.QrResponse qr(UserModel user) {
        CustomerDtos.QrResponse response = new CustomerDtos.QrResponse();
        response.setPhone(user.getPhone());
        response.setPayload(user.getPhone());
        return response;
    }

    private CustomerDtos.ProfileResponse toProfile(UserModel user, MembershipTierModel tier) {
        CustomerDtos.ProfileResponse response = new CustomerDtos.ProfileResponse();
        response.setId(user.getId());
        response.setFullName(user.getFullName());
        response.setPhone(user.getPhone());
        response.setEmail(user.getEmail());
        response.setDateOfBirth(user.getBirthDate() == null ? null : user.getBirthDate().toLocalDate());
        response.setGender(user.getGender() == null ? null : user.getGender().name());
        response.setPoints(user.getPoints());
        response.setLifetimeEarnedPoints(tierService.lifetimeEarned(user.getId(), user.getPoints()));
        response.setMemberSince(user.getCreatedAt());
        response.setQrPayload(user.getPhone());
        if (tier != null) {
            response.setTierCode(tier.getCode());
            response.setTierName(tier.getName());
            response.setPointMultiplier(tier.getPointMultiplier());
            response.setTierBenefits(tierService.parseBenefits(tier.getBenefitsJson()));
        }
        return response;
    }

    private CustomerDtos.PointHistoryItem toInvoiceHistory(OrderModel order) {
        CustomerDtos.PointHistoryItem item = new CustomerDtos.PointHistoryItem();
        item.setId(order.getId());
        item.setOrderId(order.getId());
        item.setInvoiceCode(order.getInvoiceCode());
        long points = order.getPointsEarned() == null ? 0L : order.getPointsEarned();
        item.setPoints(points);
        item.setPointsEarned(points);
        item.setOrderTotal(order.getTotal());
        item.setType("INVOICE");
        item.setCreatedAt(order.getCreatedAt());
        item.setLabel(order.getInvoiceCode() == null || order.getInvoiceCode().isBlank()
                ? "Purchase #" + order.getId()
                : "Invoice " + order.getInvoiceCode());
        return item;
    }

    private CustomerDtos.PointHistoryItem toRefundHistory(PointTransactionModel transaction) {
        CustomerDtos.PointHistoryItem item = new CustomerDtos.PointHistoryItem();
        item.setId(transaction.getId());
        item.setOrderId(transaction.getOrderId());
        item.setPoints(transaction.getPoints());
        item.setPointsEarned(transaction.getPoints());
        item.setType("REFUND_REVERSAL");
        item.setCreatedAt(transaction.getCreatedAt());
        item.setLabel("Refund adjustment");
        return item;
    }

    private CustomerDtos.TierResponse toTier(MembershipTierModel tier, Long currentId) {
        CustomerDtos.TierResponse response = new CustomerDtos.TierResponse();
        response.setId(tier.getId());
        response.setCode(tier.getCode());
        response.setName(tier.getName());
        response.setMinPoints(tier.getMinPoints());
        response.setMaxPoints(tier.getMaxPoints());
        response.setPointMultiplier(tier.getPointMultiplier());
        response.setBenefits(tierService.parseBenefits(tier.getBenefitsJson()));
        response.setSortOrder(tier.getSortOrder());
        response.setCurrent(currentId != null && currentId.equals(tier.getId()));
        return response;
    }

    private UserModel requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
