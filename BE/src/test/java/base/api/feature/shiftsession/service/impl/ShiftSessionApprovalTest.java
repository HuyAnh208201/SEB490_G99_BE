package base.api.feature.shiftsession.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.shift.repository.ShiftAssignmentRepository;
import base.api.feature.shift.repository.ShiftRepository;
import base.api.feature.shiftsession.dto.request.ReconcileShiftSessionRequest;
import base.api.feature.shiftsession.dto.response.ShiftSessionResponse;
import base.api.feature.shiftsession.repository.ShiftSessionApprovalRepository;
import base.api.feature.shiftsession.repository.ShiftSessionHighValueItemRepository;
import base.api.feature.shiftsession.repository.ShiftSessionRepository;
import base.api.shared.entity.ShiftSessionApprovalModel;
import base.api.shared.entity.ShiftSessionHighValueItemModel;
import base.api.shared.entity.ShiftSessionModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.ShiftSessionApprovalDecision;
import base.api.shared.enums.ShiftSessionStatus;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BusinessException;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftSessionApprovalTest {

    private static final Long SESSION_ID = 1L;
    private static final Long EMPLOYEE_ID = 5L;
    private static final Long MANAGER_ID = 99L;
    private static final Long BRANCH_ID = 10L;
    private static final Long OTHER_BRANCH_ID = 20L;

    @Mock
    private ShiftSessionRepository sessionRepository;

    @Mock
    private ShiftSessionHighValueItemRepository highValueItemRepository;

    @Mock
    private ShiftSessionApprovalRepository approvalRepository;

    @Mock
    private ShiftAssignmentRepository assignmentRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private IUserRepository userRepository;

    @Mock
    private IBranchRepository branchRepository;

    @Mock
    private IProductRepository productRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private ShiftSessionServiceImpl service;

    @Test
    void approveMovesSessionFromPendingApprovalToCompleted() {
        signedInAsManager(BRANCH_ID);
        ShiftSessionModel session = pendingSession();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(userRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee()));
        when(userRepository.findById(MANAGER_ID)).thenReturn(Optional.of(managerUser()));

        ReconcileShiftSessionRequest request = new ReconcileShiftSessionRequest();
        request.setApproved(true);
        request.setNote("Verified, difference within limit.");

        ShiftSessionResponse response = service.decideReconciliation(SESSION_ID, request);

        assertEquals(ShiftSessionStatus.COMPLETED, session.getStatus());
        assertEquals(MANAGER_ID, session.getApprovedBy());
        assertNotNull(session.getApprovedAt());
        assertEquals("Verified, difference within limit.", session.getManagerNote());
        assertEquals(ShiftSessionStatus.COMPLETED, response.getStatus());
        verify(approvalRepository).save(any(ShiftSessionApprovalModel.class));
    }

    @Test
    void rejectSendsSessionBackToRejectedAndClearsHandoverFlag() {
        signedInAsManager(BRANCH_ID);
        ShiftSessionModel session = pendingSession();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(userRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee()));

        ReconcileShiftSessionRequest request = new ReconcileShiftSessionRequest();
        request.setApproved(false);
        request.setNote("Difference too large, recount.");

        service.decideReconciliation(SESSION_ID, request);

        assertEquals(ShiftSessionStatus.REJECTED, session.getStatus());
        assertFalse(session.getHandoverConfirmed());
        assertEquals(MANAGER_ID, session.getApprovedBy());
        assertEquals("Difference too large, recount.", session.getManagerNote());
    }

    @Test
    void approveProductOnlyDiscrepancy() {
        signedInAsManager(BRANCH_ID);
        ShiftSessionModel session = pendingSession();
        session.setDifference(BigDecimal.ZERO);
        session.setActualCash(new BigDecimal("7000000"));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        ShiftSessionHighValueItemModel hvItem = new ShiftSessionHighValueItemModel();
        hvItem.setSessionId(SESSION_ID);
        hvItem.setProductId(101);
        hvItem.setDifference(-1);
        when(highValueItemRepository.findBySessionIdOrderByIdAsc(SESSION_ID)).thenReturn(List.of(hvItem));
        when(productRepository.findAllById(any())).thenReturn(List.of());
        when(approvalRepository.findBySessionIdOrderByDecidedAtDesc(SESSION_ID)).thenReturn(List.of());
        when(shiftRepository.findById(7L)).thenReturn(Optional.empty());
        when(userRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee()));
        when(userRepository.findById(MANAGER_ID)).thenReturn(Optional.of(managerUser()));

        ReconcileShiftSessionRequest request = new ReconcileShiftSessionRequest();
        request.setApproved(true);
        request.setNote("Product count verified.");

        ShiftSessionResponse response = service.decideReconciliation(SESSION_ID, request);

        assertEquals(ShiftSessionStatus.COMPLETED, session.getStatus());
        assertEquals(ShiftSessionStatus.COMPLETED, response.getStatus());
    }

    @Test
    void managerCannotReviewSessionFromAnotherBranch() {
        signedInAsManager(OTHER_BRANCH_ID);
        ShiftSessionModel session = pendingSession();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        ReconcileShiftSessionRequest request = new ReconcileShiftSessionRequest();
        request.setApproved(true);
        request.setNote("x");

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.decideReconciliation(SESSION_ID, request));

        assertTrue(error.getMessage().contains("another branch"));
        assertEquals(ShiftSessionStatus.PENDING_APPROVAL, session.getStatus());
    }

    private void signedInAsManager(Long branchId) {
        UserModel manager = managerUser();
        manager.setBranchId(branchId);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(manager);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.BRANCH_MANAGER);
    }

    private UserModel managerUser() {
        UserModel manager = new UserModel();
        manager.setId(MANAGER_ID);
        manager.setBranchId(BRANCH_ID);
        manager.setRole(UserRole.BRANCH_MANAGER);
        return manager;
    }

    private ShiftSessionModel pendingSession() {
        ShiftSessionModel session = new ShiftSessionModel();
        session.setId(SESSION_ID);
        session.setShiftId(7L);
        session.setEmployeeId(EMPLOYEE_ID);
        session.setBranchId(BRANCH_ID);
        session.setRole(UserRole.CASHIER);
        session.setStatus(ShiftSessionStatus.PENDING_APPROVAL);
        session.setHandoverConfirmed(true);
        session.setExpectedCash(new BigDecimal("7000000"));
        session.setActualCash(new BigDecimal("6980000"));
        session.setDifference(new BigDecimal("-20000"));
        return session;
    }

    private UserModel employee() {
        UserModel user = new UserModel();
        user.setId(EMPLOYEE_ID);
        user.setUserName("cashier01");
        user.setFirstName("Lan");
        user.setLastName("Nguyen");
        return user;
    }
}
