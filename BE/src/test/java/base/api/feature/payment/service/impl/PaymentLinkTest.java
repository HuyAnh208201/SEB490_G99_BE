package base.api.feature.payment.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.payment.dto.request.CreatePaymentRequest;
import base.api.feature.payment.dto.response.PaymentLinkResponse;
import base.api.feature.posorder.repository.OrderItemRepository;
import base.api.feature.posorder.repository.OrderRepository;
import base.api.feature.posorder.repository.PaymentRepository;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.shared.entity.OrderModel;
import base.api.shared.entity.PaymentModel;
import base.api.shared.exception.BusinessException;
import base.api.shared.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentServiceImpl#createPaymentLink} — order status gates and PayOS link creation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentLinkTest {

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

    @BeforeEach
    void urls() {
        ReflectionTestUtils.setField(service, "returnUrl", "https://example.com/return");
        ReflectionTestUtils.setField(service, "cancelUrl", "https://example.com/cancel");
    }

    @Test
    void createPaymentLinkRejectsUnknownOrder() {
        CreatePaymentRequest request = request(99L);
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        NotFoundException error =
                assertThrows(NotFoundException.class, () -> service.createPaymentLink(request));

        assertTrue(error.getMessage().contains("Order not found"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPaymentLinkRejectsNonPendingPaymentOrder() {
        OrderModel order = order(1L, "COMPLETED");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        BusinessException error =
                assertThrows(BusinessException.class, () -> service.createPaymentLink(request(1L)));

        assertTrue(error.getMessage().contains("not awaiting payment"));
    }

    @Test
    void createPaymentLinkReturnsCheckoutUrlAndUpdatesPayment() throws Exception {
        OrderModel order = order(5L, "PENDING_PAYMENT");
        PaymentModel payment = new PaymentModel();
        payment.setOrderId(5L);
        payment.setStatus("INIT");

        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(5L)).thenReturn(payment);

        var paymentRequests = mock(vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService.class);
        CreatePaymentLinkResponse payosResponse = mock(CreatePaymentLinkResponse.class);
        when(payosResponse.getCheckoutUrl()).thenReturn("https://pay.os/checkout");
        when(payosResponse.getQrCode()).thenReturn("QRDATA");
        when(payOS.paymentRequests()).thenReturn(paymentRequests);
        when(paymentRequests.create(any())).thenReturn(payosResponse);

        PaymentLinkResponse response = service.createPaymentLink(request(5L));

        assertEquals(5L, response.getOrderId());
        assertEquals("https://pay.os/checkout", response.getCheckoutUrl());
        assertEquals("PENDING", response.getStatus());
        assertEquals("PENDING", payment.getStatus());
        verify(paymentRepository).save(payment);
    }

    @Test
    void createPaymentLinkWrapsPayOsFailure() throws Exception {
        OrderModel order = order(5L, "PENDING_PAYMENT");
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(5L)).thenReturn(null);

        var paymentRequests = mock(vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService.class);
        when(payOS.paymentRequests()).thenReturn(paymentRequests);
        when(paymentRequests.create(any())).thenThrow(new RuntimeException("payos down"));

        BusinessException error =
                assertThrows(BusinessException.class, () -> service.createPaymentLink(request(5L)));

        assertTrue(error.getMessage().contains("Failed to create payment link"));
    }

    @Test
    void cancelPaymentLinkWrapsPayOsFailure() throws Exception {
        var paymentRequests = mock(vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService.class);
        when(payOS.paymentRequests()).thenReturn(paymentRequests);
        when(paymentRequests.cancel(any(Long.class), anyString()))
                .thenThrow(new RuntimeException("cancel fail"));

        BusinessException error =
                assertThrows(BusinessException.class, () -> service.cancelPaymentLink(5001L));

        assertTrue(error.getMessage().contains("Failed to cancel payment link"));
    }

    private static CreatePaymentRequest request(Long orderId) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setOrderId(orderId);
        return request;
    }

    private static OrderModel order(Long id, String status) {
        OrderModel order = new OrderModel();
        order.setId(id);
        order.setStatus(status);
        order.setTotal(new BigDecimal("150000"));
        order.setInvoiceCode("INV-001");
        order.setBranchId(10L);
        return order;
    }
}
