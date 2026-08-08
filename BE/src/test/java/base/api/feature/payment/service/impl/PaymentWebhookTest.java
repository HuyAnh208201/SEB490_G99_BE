package base.api.feature.payment.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.posorder.repository.OrderItemRepository;
import base.api.feature.posorder.repository.OrderRepository;
import base.api.feature.posorder.repository.PaymentRepository;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.shared.entity.OrderModel;
import base.api.shared.entity.PaymentModel;
import base.api.shared.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.payos.PayOS;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;
import vn.payos.service.blocking.webhooks.WebhooksService;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentServiceImpl#handleWebhook} skip / verify paths.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentWebhookTest {

    @Mock private PayOS payOS;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private BranchInventoryRepository branchInventoryRepository;
    @Mock private IUserRepository userRepository;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentServiceImpl service;

    @Test
    void handleWebhookSkipsPayOsTestOrderCode123() throws Exception {
        WebhookData data = stubVerifiedWebhook(123L);

        service.handleWebhook(Map.of("code", "00"));

        verify(paymentRepository, never()).findByTransactionRef(anyString());
        verify(paymentRepository, never()).save(any());
        verify(data).getOrderCode();
    }

    @Test
    void handleWebhookSkipsWhenPaymentMissing() throws Exception {
        stubVerifiedWebhook(5001L);
        when(paymentRepository.findByTransactionRef("5001")).thenReturn(null);

        service.handleWebhook(Map.of("code", "00"));

        verify(paymentRepository).findByTransactionRef("5001");
        verify(paymentRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void handleWebhookSkipsWhenPaymentAlreadySuccess() throws Exception {
        stubVerifiedWebhook(5001L);
        PaymentModel payment = new PaymentModel();
        payment.setOrderId(5L);
        payment.setStatus("SUCCESS");
        when(paymentRepository.findByTransactionRef("5001")).thenReturn(payment);

        service.handleWebhook(Map.of("code", "00"));

        verify(paymentRepository, never()).save(any());
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void handleWebhookMarksPaymentSuccessWhenPending() throws Exception {
        stubVerifiedWebhook(5001L);
        PaymentModel payment = new PaymentModel();
        payment.setOrderId(5L);
        payment.setStatus("PENDING");
        when(paymentRepository.findByTransactionRef("5001")).thenReturn(payment);

        OrderModel order = new OrderModel();
        order.setId(5L);
        order.setStatus("PENDING_PAYMENT");
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleWebhook(Map.of("code", "00"));

        assertEquals("SUCCESS", payment.getStatus());
        assertEquals("COMPLETED", order.getStatus());
        verify(paymentRepository).save(payment);
        verify(orderRepository).save(order);
    }

    @Test
    void handleWebhookWrapsVerifyFailure() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.readValue(anyString(), eq(Webhook.class))).thenReturn(mock(Webhook.class));
        WebhooksService webhooks = mock(WebhooksService.class);
        when(payOS.webhooks()).thenReturn(webhooks);
        when(webhooks.verify(any(Webhook.class))).thenThrow(new RuntimeException("invalid signature"));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.handleWebhook(Map.of("code", "00")));

        assertTrue(error.getMessage().contains("Webhook processing failed"));
        assertTrue(error.getMessage().contains("invalid signature"));
        verify(paymentRepository, never()).save(any());
    }

    private WebhookData stubVerifiedWebhook(long orderCode) throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(objectMapper.readValue(anyString(), eq(Webhook.class))).thenReturn(mock(Webhook.class));
        WebhooksService webhooks = mock(WebhooksService.class);
        when(payOS.webhooks()).thenReturn(webhooks);
        WebhookData data = mock(WebhookData.class);
        when(data.getOrderCode()).thenReturn(orderCode);
        when(data.getDescription()).thenReturn("desc");
        when(webhooks.verify(any(Webhook.class))).thenReturn(data);
        return data;
    }
}
