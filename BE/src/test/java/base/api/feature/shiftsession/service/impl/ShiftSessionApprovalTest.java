package base.api.feature.shiftsession.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.shift.repository.ShiftAssignmentRepository;
import base.api.feature.shift.repository.ShiftRepository;
import base.api.feature.shiftsession.dto.response.ShiftSessionResponse;
import base.api.feature.shiftsession.repository.ShiftSessionHighValueItemRepository;
import base.api.feature.shiftsession.repository.ShiftSessionRepository;
import base.api.shared.entity.ShiftSessionModel;
import base.api.shared.entity.UserModel;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private ShiftAssignmentRepository assignmentRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private IUserRepository userRepository;

    @Mock
    private IBranchRepository branchRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private ShiftSessionServiceImpl service;

    @Test
    void approveMovesSessionFromPendingApprovalToApproved() {
        signedInAsManager(BRANCH_ID);
        ShiftSessionModel session = pendingSession();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(userRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee()));

        ShiftSessionResponse response = service.approveSession(SESSION_ID, "Verified, difference within limit.");

        assertEquals(ShiftSessionStatus.APPROVED, session.getStatus());
        assertEquals(MANAGER_ID, session.getReviewedBy());
        assertNotNull(session.getReviewedAt());
        assertEquals("Verified, difference within limit.", session.getReviewNote());
        assertEquals(ShiftSessionStatus.APPROVED, response.getStatus());
        assertEquals("Verified, difference within limit.", response.getReviewNote());
    }

    @Test
    void rejectSendsSessionBackToPendingHandoverAndClearsHandoverFlag() {
        signedInAsManager(BRANCH_ID);
        ShiftSessionModel session = pendingSession();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(userRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee()));

        service.rejectSession(SESSION_ID, "Difference too large, recount.");

        assertEquals(ShiftSessionStatus.PENDING_HANDOVER, session.getStatus());
        assertFalse(session.getHandoverConfirmed());
        assertEquals(MANAGER_ID, session.getReviewedBy());
        assertEquals("Difference too large, recount.", session.getReviewNote());
    }

    @Test
    void managerCannotReviewSessionFromAnotherBranch() {
        signedInAsManager(OTHER_BRANCH_ID);
        ShiftSessionModel session = pendingSession(); // branch 10, manager is branch 20
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.approveSession(SESSION_ID, "x"));

        assertTrue(error.getMessage().contains("own branch"));
        assertEquals(ShiftSessionStatus.PENDING_APPROVAL, session.getStatus());
    }

    // ---------------------------------------------------------------------

    private void signedInAsManager(Long branchId) {
        UserModel manager = new UserModel();
        manager.setId(MANAGER_ID);
        manager.setBranchId(branchId);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(manager);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.BRANCH_MANAGER);
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
