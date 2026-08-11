package base.api.feature.payment.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.posorder.repository.OrderItemRepository;
import base.api.feature.posorder.repository.OrderRepository;
import base.api.feature.posorder.repository.PaymentRepository;
import base.api.feature.posorder.service.VoucherReleaseService;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.shared.entity.OrderItemModel;
import base.api.shared.entity.OrderModel;
import base.api.shared.entity.PaymentModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.payos.PayOS;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Huỷ/hết hạn đơn PAYOS phải nhả lại mã giảm giá đã khoá lúc chốt đơn.
 * Không nhả thì khách mất mã vĩnh viễn dù chưa hề thanh toán.
 * Chi tiết cách nhả từng dòng nằm ở {@code VoucherReleaseServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentVoucherReleaseTest {

    private static final long ORDER_CODE = 5001L;
    private static final Long ORDER_ID = 42L;
    private static final Long BRANCH_ID = 10L;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private PayOS payOS;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private VoucherReleaseService voucherReleaseService;
    @Mock private BranchInventoryRepository branchInventoryRepository;
    @Mock private IUserRepository userRepository;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentServiceImpl service;

    private PaymentModel payment;

    @BeforeEach
    void setUp() {
        payment = new PaymentModel();
        payment.setId(7L);
        payment.setOrderId(ORDER_ID);
        payment.setStatus("PENDING");
        payment.setTransactionRef(String.valueOf(ORDER_CODE));
        when(paymentRepository.findByTransactionRef(String.valueOf(ORDER_CODE))).thenReturn(payment);
        when(orderItemRepository.findByOrderIdIn(List.of(ORDER_ID))).thenReturn(List.of(item(1, 2)));
    }

    @Test
    void cancellingPendingPayosOrderReleasesTheVoucher() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("PENDING_PAYMENT")));

        service.cancelPaymentLink(ORDER_CODE);

        verify(voucherReleaseService).releaseForOrder(ORDER_ID);
        // Kho cũng phải được hoàn cùng lúc — nhả mã không được thay thế việc nhả hàng.
        verify(branchInventoryRepository).addStock(BRANCH_ID, 1, 2);
    }

    @Test
    void cancellingSetsOrderAndPaymentToCancelled() {
        OrderModel order = order("PENDING_PAYMENT");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        service.cancelPaymentLink(ORDER_CODE);

        assertEquals("CANCELLED", order.getStatus());
        assertEquals("CANCELLED", payment.getStatus());
    }

    /**
     * Cashier bấm huỷ rồi FE lại poll thấy CANCELLED — lần hai đơn đã rời
     * PENDING_PAYMENT nên không được nhả mã (và nhả kho) thêm lần nữa.
     */
    @Test
    void alreadyCancelledOrderIsNotReleasedTwice() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("CANCELLED")));

        service.cancelPaymentLink(ORDER_CODE);

        verify(voucherReleaseService, never()).releaseForOrder(anyLong());
        verify(branchInventoryRepository, never()).addStock(anyLong(), anyInt(), anyInt());
    }

    /** Đơn đã trả tiền xong thì mã coi như đã tiêu, huỷ link không được nhả lại. */
    @Test
    void completedOrderIsNotReleased() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("COMPLETED")));

        service.cancelPaymentLink(ORDER_CODE);

        verify(voucherReleaseService, never()).releaseForOrder(anyLong());
    }

    private OrderModel order(String status) {
        OrderModel order = new OrderModel();
        order.setId(ORDER_ID);
        order.setBranchId(BRANCH_ID);
        order.setStatus(status);
        order.setPointsEarned(0L);
        order.setPointsRedeemed(0L);
        return order;
    }

    private OrderItemModel item(Integer productId, Integer quantity) {
        OrderItemModel item = new OrderItemModel();
        item.setOrderId(ORDER_ID);
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }
}
