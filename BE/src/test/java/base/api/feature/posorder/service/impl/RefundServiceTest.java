package base.api.feature.posorder.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.posorder.dto.response.RefundResponse;
import base.api.feature.posorder.repository.OrderItemRepository;
import base.api.feature.posorder.repository.OrderRefundRepository;
import base.api.feature.posorder.repository.OrderRepository;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundServiceTest {

    private static final Long BRANCH_ID = 10L;
    private static final Long ORDER_ID = 1L;
    private static final Long REFUND_ID = 7L;

    @Mock private OrderRefundRepository orderRefundRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private BranchInventoryRepository branchInventoryRepository;
    @Mock private IUserRepository userRepository;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private base.api.shared.security.CurrentUserProvider currentUserProvider;

    @InjectMocks
    private RefundServiceImpl service;

    // =========================================================================
    // (1) requestRefund after the 5-minute window -> BusinessException
    // =========================================================================
    @Test
    void requestRefundAfterFiveMinuteWindowIsRejected() {
        asCashier();
        OrderModel order = completedOrder();
        order.setCreatedAt(LocalDateTime.now().minusMinutes(6));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.requestRefund(ORDER_ID, "Wrong item scanned"));

        assertTrue(error.getMessage().contains("5-minute refund window"));
        verify(orderRefundRepository, never()).save(any());
    }

    @Test
    void requestRefundWithBlankReasonIsRejected() {
        asCashier();

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.requestRefund(ORDER_ID, "   "));

        assertTrue(error.getMessage().toLowerCase().contains("reason"));
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void requestRefundRejectsWhenCreatedAtIsNull() {
        asCashier();
        OrderModel order = completedOrder();
        order.setCreatedAt(null);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.requestRefund(ORDER_ID, "Wrong item"));

        assertTrue(error.getMessage().contains("5-minute refund window"));
    }

    @Test
    void requestRefundAutomaticallyRestocksAndApprovesWithinWindow() {
        asCashier();
        OrderModel order = completedOrder();
        order.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRefundRepository.existsByOrderIdAndStatusIn(eq(ORDER_ID), any())).thenReturn(false);
        OrderItemModel item = new OrderItemModel();
        item.setProductId(50);
        item.setQuantity(3);
        when(orderItemRepository.findByOrderIdIn(List.of(ORDER_ID))).thenReturn(List.of(item));
        when(orderRefundRepository.save(any())).thenAnswer(call -> {
            OrderRefundModel refund = call.getArgument(0);
            refund.setId(REFUND_ID);
            return refund;
        });
        when(orderRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        RefundResponse response = service.requestRefund(ORDER_ID, "Wrong item scanned");

        assertEquals("APPROVED", response.getStatus());
        assertEquals(REFUND_ID, response.getRefundId());
        assertEquals("REFUNDED", order.getStatus());
        verify(branchInventoryRepository).addStock(BRANCH_ID, 50, 3);
        verify(orderRefundRepository).save(any());
    }

    @Test
    void requestRefundRejectsOrderContainingNonRefundableProduct() {
        asCashier();
        OrderModel order = completedOrder();
        order.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRefundRepository.existsByOrderIdAndStatusIn(eq(ORDER_ID), any())).thenReturn(false);

        OrderItemModel item = new OrderItemModel();
        item.setProductName("Gift card");
        item.setProductId(50);
        item.setQuantity(1);
        item.setRefundable(false);
        when(orderItemRepository.findByOrderIdIn(List.of(ORDER_ID))).thenReturn(List.of(item));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.requestRefund(ORDER_ID, "Customer changed mind"));

        assertTrue(error.getMessage().contains("Gift card"));
        verify(branchInventoryRepository, never()).addStock(anyLong(), anyInt(), anyInt());
        verify(orderRefundRepository, never()).save(any());
    }

    @Test
    void requestRefundRejectsOrderFromAnotherBranch() {
        asCashier();
        OrderModel order = completedOrder();
        order.setBranchId(99L);
        order.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.requestRefund(ORDER_ID, "Wrong item"));

        assertTrue(error.getMessage().contains("another branch"));
        verify(orderRefundRepository, never()).save(any());
    }

    @Test
    void requestRefundRejectsNonCompletedOrder() {
        asCashier();
        OrderModel order = completedOrder();
        order.setStatus("PENDING_PAYMENT");
        order.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.requestRefund(ORDER_ID, "Changed mind"));

        assertTrue(error.getMessage().toLowerCase().contains("completed"));
        verify(orderRefundRepository, never()).save(any());
    }

    @Test
    void requestRefundRejectsUnknownOrder() {
        asCashier();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.requestRefund(ORDER_ID, "Wrong item"));
    }

    // =========================================================================
    // (3) requestRefund blocked when a PENDING refund already exists
    // =========================================================================
    @Test
    void requestRefundWhenOneIsAlreadyInProgressIsBlocked() {
        asCashier();
        OrderModel order = completedOrder();
        order.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRefundRepository.existsByOrderIdAndStatusIn(eq(ORDER_ID), any()))
                .thenReturn(true);

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.requestRefund(ORDER_ID, "Customer changed mind"));

        assertTrue(error.getMessage().toLowerCase().contains("already"));
        verify(orderRefundRepository, never()).save(any());
    }

    // =========================================================================
    // (2) approveRefund restocks exact qty, marks order REFUNDED and refund APPROVED
    // =========================================================================
    @Test
    void approveRefundRestocksRecallsPointsAndMarksOrderRefunded() {
        asManager();
        OrderRefundModel refund = pendingRefund();
        when(orderRefundRepository.findById(REFUND_ID)).thenReturn(Optional.of(refund));

        OrderModel order = completedOrder();
        order.setCustomerId(200L);
        order.setPointsEarned(5L);
        order.setPointsRedeemed(2L);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        OrderItemModel item = new OrderItemModel();
        item.setOrderId(ORDER_ID);
        item.setProductId(50);
        item.setQuantity(3);
        when(orderItemRepository.findByOrderIdIn(List.of(ORDER_ID))).thenReturn(List.of(item));
        when(orderRefundRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        RefundResponse response = service.approveRefund(REFUND_ID, "Approved by BM");

        // Restock exactly 3 units into the order branch inventory.
        verify(branchInventoryRepository).addStock(BRANCH_ID, 50, 3);
        // Recall earned points and restore redeemed points.
        verify(userRepository).deductPointsAtomic(200L, 5L);
        verify(userRepository).refundPointsAtomic(200L, 2L);
        // Point reversal history: earned recall is negative (-5), redeemed restore is positive (+2).
        ArgumentCaptor<PointTransactionModel> reversals = ArgumentCaptor.forClass(PointTransactionModel.class);
        verify(pointTransactionRepository, times(2)).save(reversals.capture());
        assertEquals(-5L, reversals.getAllValues().get(0).getPoints());
        assertEquals(2L, reversals.getAllValues().get(1).getPoints());
        assertEquals("REFUND_REVERSAL", reversals.getAllValues().get(0).getType());
        // Order leaves revenue; refund request becomes APPROVED.
        assertEquals("REFUNDED", order.getStatus());
        assertEquals("APPROVED", refund.getStatus());
        assertEquals("APPROVED", response.getStatus());
        assertEquals(REFUND_ID, response.getRefundId());
    }

    // =========================================================================
    // (4) reject keeps order COMPLETED
    // =========================================================================
    @Test
    void rejectKeepsOrderCompletedAndDoesNotRestock() {
        asManager();
        OrderRefundModel refund = pendingRefund();
        when(orderRefundRepository.findById(REFUND_ID)).thenReturn(Optional.of(refund));
        when(orderRefundRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        OrderModel order = completedOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        RefundResponse response = service.rejectRefund(REFUND_ID, "Refund not justified");

        assertEquals("REJECTED", refund.getStatus());
        assertEquals("REJECTED", response.getStatus());
        // Order untouched: stays COMPLETED, no restock, order not rewritten.
        assertEquals("COMPLETED", order.getStatus());
        verify(orderRepository, never()).save(any());
        verify(branchInventoryRepository, never()).addStock(anyLong(), anyInt(), anyInt());
    }

    @Test
    void approveRefundWithoutCustomerSkipsPointReversal() {
        asManager();
        OrderRefundModel refund = pendingRefund();
        when(orderRefundRepository.findById(REFUND_ID)).thenReturn(Optional.of(refund));

        OrderModel order = completedOrder();
        order.setCustomerId(null);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderIdIn(List.of(ORDER_ID))).thenReturn(List.of());
        when(orderRefundRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        RefundResponse response = service.approveRefund(REFUND_ID, "OK");

        assertEquals("APPROVED", response.getStatus());
        assertEquals("REFUNDED", order.getStatus());
        verify(userRepository, never()).deductPointsAtomic(anyLong(), anyLong());
        verify(userRepository, never()).refundPointsAtomic(anyLong(), anyLong());
    }

    @Test
    void approveRefundRejectsCrossBranchManager() {
        asManager();
        OrderRefundModel refund = pendingRefund();
        refund.setBranchId(99L);
        when(orderRefundRepository.findById(REFUND_ID)).thenReturn(Optional.of(refund));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.approveRefund(REFUND_ID, "note"));

        assertTrue(error.getMessage().contains("own branch"));
    }

    // =========================================================================
    // Role / note guards
    // =========================================================================

    @Test
    void getPendingRefundsRejectsNonBranchManager() {
        asCashier();

        BusinessException error = assertThrows(BusinessException.class, () -> service.getPendingRefunds());

        assertTrue(error.getMessage().contains("Only branch managers can review refunds."));
        verify(orderRefundRepository, never()).findByBranchIdAndStatusOrderByCreatedAtDesc(anyLong(), any());
    }

    @Test
    void rejectRefundRejectsBlankNote() {
        asManager();

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.rejectRefund(REFUND_ID, "   "));

        assertTrue(error.getMessage().contains("A note is required to reject a refund."));
        verify(orderRefundRepository, never()).findById(any());
    }

    @Test
    void requestRefundRejectsNonCashier() {
        asManager();

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.requestRefund(ORDER_ID, "Wrong item"));

        assertTrue(error.getMessage().contains("Only cashiers can request a refund."));
        verify(orderRepository, never()).findById(any());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void asCashier() {
        UserModel cashier = new UserModel();
        cashier.setId(3L);
        cashier.setBranchId(BRANCH_ID);
        cashier.setRole(UserRole.CASHIER);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.CASHIER);
    }

    private void asManager() {
        UserModel manager = new UserModel();
        manager.setId(5L);
        manager.setBranchId(BRANCH_ID);
        manager.setRole(UserRole.BRANCH_MANAGER);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(manager);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.BRANCH_MANAGER);
    }

    private OrderModel completedOrder() {
        OrderModel order = new OrderModel();
        order.setId(ORDER_ID);
        order.setBranchId(BRANCH_ID);
        order.setStatus("COMPLETED");
        order.setTotal(new BigDecimal("24000"));
        order.setCreatedAt(LocalDateTime.now());
        return order;
    }

    private OrderRefundModel pendingRefund() {
        OrderRefundModel refund = new OrderRefundModel();
        refund.setId(REFUND_ID);
        refund.setOrderId(ORDER_ID);
        refund.setBranchId(BRANCH_ID);
        refund.setRequestedBy(3L);
        refund.setReason("Wrong item scanned");
        refund.setStatus("PENDING");
        refund.setCreatedAt(LocalDateTime.now());
        return refund;
    }
}
