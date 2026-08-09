package base.api.feature.posorder.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.posorder.repository.OrderItemRepository;
import base.api.feature.posorder.repository.OrderRefundRepository;
import base.api.feature.posorder.repository.OrderRepository;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BusinessException;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Extra unit tests for {@link RefundServiceImpl} role / note guards.
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceExtraTest {

    @Mock private OrderRefundRepository orderRefundRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private BranchInventoryRepository branchInventoryRepository;
    @Mock private IUserRepository userRepository;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private RefundServiceImpl service;

    @Test
    void getPendingRefundsRejectsNonBranchManager() {
        UserModel cashier = user(3L, 10L, UserRole.CASHIER);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.CASHIER);

        BusinessException error = assertThrows(BusinessException.class, () -> service.getPendingRefunds());

        assertTrue(error.getMessage().contains("Only branch managers can review refunds."));
        verify(orderRefundRepository, never()).findByBranchIdAndStatusOrderByCreatedAtDesc(anyLong(), any());
    }

    @Test
    void getPendingRefundsRejectsBranchManagerWithoutBranch() {
        UserModel manager = user(5L, null, UserRole.BRANCH_MANAGER);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(manager);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.BRANCH_MANAGER);

        BusinessException error = assertThrows(BusinessException.class, () -> service.getPendingRefunds());

        assertTrue(error.getMessage().contains("Branch manager is not assigned to a branch."));
        verify(orderRefundRepository, never()).findByBranchIdAndStatusOrderByCreatedAtDesc(anyLong(), any());
    }

    @Test
    void rejectRefundRejectsBlankNote() {
        UserModel manager = user(5L, 10L, UserRole.BRANCH_MANAGER);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(manager);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.BRANCH_MANAGER);

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.rejectRefund(7L, "   "));

        assertTrue(error.getMessage().contains("A note is required to reject a refund."));
        verify(orderRefundRepository, never()).findById(any());
    }

    @Test
    void requestRefundRejectsNonCashier() {
        UserModel manager = user(5L, 10L, UserRole.BRANCH_MANAGER);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(manager);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.BRANCH_MANAGER);

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.requestRefund(1L, "Wrong item"));

        assertTrue(error.getMessage().contains("Only cashiers can request a refund."));
        verify(orderRepository, never()).findById(any());
    }

    private static UserModel user(Long id, Long branchId, UserRole role) {
        UserModel user = new UserModel();
        user.setId(id);
        user.setBranchId(branchId);
        user.setRole(role);
        return user;
    }
}
