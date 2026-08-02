package base.api.feature.payment.service.impl;

import base.api.feature.payment.dto.request.CreatePaymentRequest;
import base.api.feature.payment.dto.response.PaymentLinkResponse;
import base.api.feature.payment.dto.response.PaymentStatusResponse;
import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.payment.service.IPaymentService;
import base.api.feature.posorder.repository.OrderItemRepository;
import base.api.feature.posorder.repository.OrderRepository;
import base.api.feature.posorder.repository.PaymentRepository;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.shared.entity.OrderItemModel;
import base.api.shared.entity.OrderModel;
import base.api.shared.entity.PaymentModel;
import base.api.shared.entity.PointTransactionModel;
import base.api.shared.exception.BusinessException;
import base.api.shared.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PaymentServiceImpl implements IPaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    @Autowired
    private PayOS payOS;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private BranchInventoryRepository branchInventoryRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${payos.return-url}")
    private String returnUrl;

    @Value("${payos.cancel-url}")
    private String cancelUrl;

    // =========================================================================
    // Tạo payment link
    // =========================================================================

    @Override
    @Transactional
    public PaymentLinkResponse createPaymentLink(CreatePaymentRequest request) {
        OrderModel order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new NotFoundException("Order not found."));

        // Kiểm tra đơn hàng phải ở trạng thái PENDING_PAYMENT
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new BusinessException(
                    "Order is not awaiting payment. Current status: " + order.getStatus());
        }

        // orderCode phải là số nguyên dương unique — dùng order.id nhân 1000 + mili giây
        long orderCode = order.getId() * 1000 + (System.currentTimeMillis() % 1000);

        long amount = order.getTotal().longValue();
        String description = "Don hang " + order.getInvoiceCode();
        // payOS giới hạn description 25 ký tự
        if (description.length() > 25) {
            description = description.substring(0, 25);
        }

        try {
            PaymentLinkItem item = PaymentLinkItem.builder()
                    .name(order.getInvoiceCode() != null ? order.getInvoiceCode() : "Order #" + order.getId())
                    .quantity(1)
                    .price(amount)
                    .build();

            CreatePaymentLinkRequest paymentRequest = CreatePaymentLinkRequest.builder()
                    .orderCode(orderCode)
                    .amount(amount)
                    .description(description)
                    .returnUrl(returnUrl)
                    .cancelUrl(cancelUrl)
                    .items(List.of(item))
                    .build();

            CreatePaymentLinkResponse response = payOS.paymentRequests().create(paymentRequest);

            // Cập nhật payment record với orderCode
            PaymentModel payment = paymentRepository.findByOrderId(order.getId());
            if (payment != null) {
                payment.setTransactionRef(String.valueOf(orderCode));
                payment.setStatus("PENDING");
                paymentRepository.save(payment);
            }

            return PaymentLinkResponse.builder()
                    .orderId(order.getId())
                    .orderCode(orderCode)
                    .amount(amount)
                    .checkoutUrl(response.getCheckoutUrl())
                    .qrCode(response.getQrCode())
                    .status("PENDING")
                    .build();

        } catch (Exception e) {
            log.error("Failed to create PayOS payment link for order {}: {}",
                    order.getId(), e.getMessage(), e);
            throw new BusinessException("Failed to create payment link: " + e.getMessage());
        }
    }

    // =========================================================================
    // Kiểm tra trạng thái
    // =========================================================================

    @Override
    public PaymentStatusResponse getPaymentStatus(long orderCode) {
        try {
            PaymentLink paymentInfo = payOS.paymentRequests().get(orderCode);

            // Tìm payment record qua transaction_ref
            PaymentModel payment = paymentRepository.findByTransactionRef(String.valueOf(orderCode));

            String status = paymentInfo.getStatus().name(); // PENDING, PAID, CANCELLED, EXPIRED
            Long orderId = payment != null ? payment.getOrderId() : null;

            // Nếu payOS báo PAID mà local chưa cập nhật → cập nhật luôn
            if ("PAID".equals(status) && payment != null && !"SUCCESS".equals(payment.getStatus())) {
                markPaymentSuccess(payment, orderCode);
            }

            // Khách tự hủy trên trang payOS hoặc link hết hạn: không có ai gọi
            // cancelPaymentLink nên đơn sẽ kẹt ở PENDING_PAYMENT và ôm luôn tồn kho
            // đã trừ. Nhả ngay khi phát hiện để kho không bị lệch.
            if (("CANCELLED".equals(status) || "EXPIRED".equals(status)) && payment != null) {
                markPaymentCancelled(payment);
            }

            return PaymentStatusResponse.builder()
                    .orderCode(orderCode)
                    .orderId(orderId)
                    .status(status)
                    .transactionRef(payment != null ? payment.getTransactionRef() : null)
                    .build();

        } catch (Exception e) {
            log.error("Failed to get PayOS payment status for orderCode {}: {}",
                    orderCode, e.getMessage());
            throw new BusinessException("Failed to check payment status: " + e.getMessage());
        }
    }

    // =========================================================================
    // Hủy payment link
    // =========================================================================

    @Override
    @Transactional
    public void cancelPaymentLink(long orderCode) {
        try {
            payOS.paymentRequests().cancel(Long.valueOf(orderCode), "Cancelled by cashier");

            // Cập nhật local
            PaymentModel payment = paymentRepository.findByTransactionRef(String.valueOf(orderCode));
            if (payment != null) {
                markPaymentCancelled(payment);
            }
        } catch (Exception e) {
            log.error("Failed to cancel PayOS payment link for orderCode {}: {}",
                    orderCode, e.getMessage());
            throw new BusinessException("Failed to cancel payment link: " + e.getMessage());
        }
    }

    // =========================================================================
    // Webhook handler
    // =========================================================================

    @Override
    @Transactional
    public void handleWebhook(Map<String, Object> body) {
        try {
            // Chuyển body Map thành JSON string rồi parse thành Webhook object
            String jsonBody = objectMapper.writeValueAsString(body);
            Webhook webhookBody = objectMapper.readValue(jsonBody, Webhook.class);

            // Verify signature bằng PayOS SDK — ném exception nếu invalid
            WebhookData data = payOS.webhooks().verify(webhookBody);

            long orderCode = data.getOrderCode();
            log.info("PayOS webhook received: orderCode={}, desc={}",
                    orderCode, data.getDescription());

            // Bỏ qua webhook test với orderCode = 123 (payOS gửi khi đăng ký webhook URL)
            if (orderCode == 123) {
                log.info("Skipping PayOS test webhook (orderCode=123)");
                return;
            }

            // Tìm payment record
            PaymentModel payment = paymentRepository.findByTransactionRef(String.valueOf(orderCode));
            if (payment == null) {
                log.warn("No payment found for orderCode={}", orderCode);
                return;
            }

            // Đã xử lý rồi thì skip
            if ("SUCCESS".equals(payment.getStatus())) {
                log.info("Payment already marked SUCCESS for orderCode={}", orderCode);
                return;
            }

            markPaymentSuccess(payment, orderCode);
            log.info("Payment marked SUCCESS for orderCode={}, orderId={}",
                    orderCode, payment.getOrderId());

        } catch (Exception e) {
            log.error("Webhook processing failed: {}", e.getMessage(), e);
            throw new BusinessException("Webhook processing failed: " + e.getMessage());
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Đóng đơn chưa trả tiền và nhả lại hàng. Chỉ đơn còn PENDING_PAYMENT mới được
     * nhả — chặn hoàn kho hai lần khi cashier bấm hủy rồi FE lại poll thấy CANCELLED.
     */
    private void markPaymentCancelled(PaymentModel payment) {
        payment.setStatus("CANCELLED");
        paymentRepository.save(payment);

        orderRepository.findById(payment.getOrderId()).ifPresent(order -> {
            if (!"PENDING_PAYMENT".equals(order.getStatus())) {
                return;
            }
            releaseOrder(order);
            order.setStatus("CANCELLED");
            orderRepository.save(order);
        });
    }

    /**
     * Nhả lại những gì đã bị trừ lúc chốt đơn PAYOS: đơn được ghi và trừ kho ngay khi
     * tạo link thanh toán, nên hủy link mà không hoàn lại sẽ mất hàng và mất điểm của khách.
     * Cùng cách xử lý với duyệt hoàn đơn — xem RefundServiceImpl#approveRefund.
     */
    private void releaseOrder(OrderModel order) {
        List<OrderItemModel> items = orderItemRepository.findByOrderIdIn(List.of(order.getId()));
        for (OrderItemModel item : items) {
            branchInventoryRepository.addStock(
                    order.getBranchId(), item.getProductId(), item.getQuantity());
        }

        if (order.getCustomerId() == null) {
            return;
        }
        // Đảo điểm best-effort — KHÔNG được làm fail việc hủy đơn.
        long earned = order.getPointsEarned() == null ? 0L : order.getPointsEarned();
        long redeemed = order.getPointsRedeemed() == null ? 0L : order.getPointsRedeemed();
        if (earned > 0) {
            userRepository.deductPointsAtomic(order.getCustomerId(), earned);
        }
        if (redeemed > 0) {
            userRepository.refundPointsAtomic(order.getCustomerId(), redeemed);
        }
        try {
            if (earned > 0) {
                savePointReversal(order, -earned);
            }
            if (redeemed > 0) {
                savePointReversal(order, redeemed);
            }
        } catch (Exception ex) {
            log.warn("Failed to record point reversal for cancelled order {}: {}",
                    order.getId(), ex.getMessage());
        }
    }

    private void savePointReversal(OrderModel order, long points) {
        PointTransactionModel transaction = new PointTransactionModel();
        transaction.setCustomerId(order.getCustomerId());
        transaction.setOrderId(order.getId());
        transaction.setPoints(points);
        transaction.setType("REFUND_REVERSAL");
        transaction.setCreatedAt(LocalDateTime.now());
        pointTransactionRepository.save(transaction);
    }

    private void markPaymentSuccess(PaymentModel payment, long orderCode) {
        payment.setStatus("SUCCESS");
        paymentRepository.save(payment);

        // Cập nhật đơn hàng → COMPLETED
        orderRepository.findById(payment.getOrderId()).ifPresent(order -> {
            if ("PENDING_PAYMENT".equals(order.getStatus())) {
                order.setStatus("COMPLETED");
                orderRepository.save(order);
            }
        });
    }
}
