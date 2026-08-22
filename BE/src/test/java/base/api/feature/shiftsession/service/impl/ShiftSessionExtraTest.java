package base.api.feature.shiftsession.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.inventorycount.repository.InventoryCountSessionRepository;
import base.api.feature.posorder.repository.PaymentRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.shift.repository.ShiftAssignmentRepository;
import base.api.feature.shift.repository.ShiftRepository;
import base.api.feature.shiftsession.dto.request.CloseInventoryShiftRequest;
import base.api.feature.shiftsession.dto.request.ConfirmHandoverRequest;
import base.api.feature.shiftsession.dto.request.ConfirmOpeningFundRequest;
import base.api.feature.shiftsession.dto.request.ConfirmVerificationRequest;
import base.api.feature.shiftsession.dto.request.SaveClosingDraftRequest;
import base.api.feature.shiftsession.dto.request.StartShiftRequest;
import base.api.feature.shiftsession.dto.response.ShiftSessionResponse;
import base.api.feature.shiftsession.repository.ShiftSessionApprovalRepository;
import base.api.feature.shiftsession.repository.ShiftSessionHighValueItemRepository;
import base.api.feature.shiftsession.repository.ShiftSessionRepository;
import base.api.shared.entity.ShiftAssignmentModel;
import base.api.shared.entity.ShiftModel;
import base.api.shared.entity.ShiftSessionHighValueItemModel;
import base.api.shared.entity.ShiftSessionModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.FundTransferMethod;
import base.api.shared.enums.ShiftSessionStatus;
import base.api.shared.enums.ShiftStatus;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BusinessException;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Extra ShiftSessionServiceImpl coverage beyond decideReconciliation (see ShiftSessionApprovalTest).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShiftSessionExtraTest {

    private static final Long BRANCH_ID = 10L;
    private static final Long EMPLOYEE_ID = 5L;
    private static final Long SHIFT_ID = 7L;
    private static final Long SESSION_ID = 1L;
    private static final Long ASSIGNMENT_ID = 40L;

    @Mock private ShiftSessionRepository sessionRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ShiftSessionHighValueItemRepository highValueItemRepository;
    @Mock private ShiftSessionApprovalRepository approvalRepository;
    @Mock private ShiftAssignmentRepository assignmentRepository;
    @Mock private ShiftRepository shiftRepository;
    @Mock private IUserRepository userRepository;
    @Mock private IBranchRepository branchRepository;
    @Mock private BranchInventoryRepository branchInventoryRepository;
    @Mock private IProductRepository productRepository;
    @Mock private InventoryCountSessionRepository inventoryCountSessionRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ShiftSessionServiceImpl service;

    @BeforeEach
    void allowShiftTestsOutsideStoreHours() {
        ReflectionTestUtils.setField(service, "allowOutsideHoursTestMode", true);
    }

    // -------------------------------------------------------------------------
    // getCurrent / start / opening fund
    // -------------------------------------------------------------------------

    @Test
    void getCurrentRejectsNonCashier() {
        signedInAs(UserRole.BRANCH_MANAGER);

        BusinessException error = assertThrows(BusinessException.class, () -> service.getCurrent());

        assertTrue(error.getMessage().contains("Only cashiers can use shift sessions."));
    }

    @Test
    void getCurrentReturnsEmptyShellWhenNoAssignment() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        when(sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(EMPLOYEE_ID), anyList()))
                .thenReturn(Optional.empty());
        when(assignmentRepository.findPublishedAssignmentsOverlapping(anyLong(), any(), any(), eq(ShiftStatus.PUBLISHED)))
                .thenReturn(List.of());
        when(assignmentRepository.findPublishedAssignmentsForStaffBetween(anyLong(), any(), any(), eq(ShiftStatus.PUBLISHED)))
                .thenReturn(List.of());

        ShiftSessionResponse response = service.getCurrent();

        assertEquals(EMPLOYEE_ID, response.getEmployeeId());
        assertEquals(BRANCH_ID, response.getBranchId());
        assertNull(response.getId());
    }

    @Test
    void getCurrentReschedulesCompletedSessionWhenClockRewoundWithinShiftWindow() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        when(sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(EMPLOYEE_ID), anyList()))
                .thenReturn(Optional.empty());
        ShiftAssignmentModel assignment = publishedAssignment(cashier);
        when(assignmentRepository.findPublishedAssignmentsForStaffBetween(
                eq(EMPLOYEE_ID), any(), any(), eq(ShiftStatus.PUBLISHED)))
                .thenReturn(List.of(assignment));
        stubCurrentAssignment(assignment);
        ShiftSessionModel completed = openSession(ShiftSessionStatus.COMPLETED);
        completed.setClosedAt(LocalDateTime.now().plusHours(2));
        when(sessionRepository.findFirstByShiftIdAndEmployeeIdOrderByIdDesc(SHIFT_ID, EMPLOYEE_ID))
                .thenReturn(Optional.of(completed));
        when(sessionRepository.save(any(ShiftSessionModel.class))).thenAnswer(inv -> inv.getArgument(0));
        stubToResponseDeps(cashier);

        ShiftSessionResponse response = service.getCurrent();

        assertEquals(ShiftSessionStatus.SCHEDULED, response.getStatus());
        assertEquals(ShiftSessionStatus.SCHEDULED, completed.getStatus());
        assertNull(completed.getClosedAt());
        verify(sessionRepository, org.mockito.Mockito.atLeastOnce()).save(completed);
    }

    @Test
    void getCurrentKeepsDemoCashierClosingSessionActive() {
        UserModel cashier = cashier();
        cashier.setEmail("demo_cashier@chainstore.vn");
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        ShiftSessionModel closing = openSession(ShiftSessionStatus.CLOSING);
        when(sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(EMPLOYEE_ID), anyList()))
                .thenReturn(Optional.of(closing));
        stubToResponseDeps(cashier);

        ShiftSessionResponse response = service.getCurrent();

        assertEquals(ShiftSessionStatus.CLOSING, response.getStatus());
        assertEquals(ShiftSessionStatus.CLOSING, closing.getStatus());
        assertNull(closing.getClosedAt());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void getCurrentCreatesFreshDemoShiftWhenOverlappingAssignmentIsCompleted() {
        UserModel cashier = cashier();
        cashier.setEmail("demo_cashier@chainstore.vn");
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        when(sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(EMPLOYEE_ID), anyList()))
                .thenReturn(Optional.empty());
        ShiftAssignmentModel oldAssignment = publishedAssignment(cashier);
        when(assignmentRepository.findPublishedAssignmentsOverlapping(
                eq(EMPLOYEE_ID), any(), any(), eq(ShiftStatus.PUBLISHED)))
                .thenReturn(List.of(oldAssignment));
        ShiftSessionModel completed = openSession(ShiftSessionStatus.COMPLETED);
        when(sessionRepository.findFirstByShiftIdAndEmployeeIdOrderByIdDesc(SHIFT_ID, EMPLOYEE_ID))
                .thenReturn(Optional.of(completed));
        when(shiftRepository.save(any(ShiftModel.class))).thenAnswer(invocation -> {
            ShiftModel shift = invocation.getArgument(0);
            shift.setId(99L);
            return shift;
        });
        when(assignmentRepository.save(any(ShiftAssignmentModel.class))).thenAnswer(invocation -> {
            ShiftAssignmentModel assignment = invocation.getArgument(0);
            assignment.setId(100L);
            return assignment;
        });
        when(sessionRepository.findFirstByShiftIdAndEmployeeIdOrderByIdDesc(99L, EMPLOYEE_ID))
                .thenReturn(Optional.empty());
        when(sessionRepository.save(any(ShiftSessionModel.class))).thenAnswer(invocation -> {
            ShiftSessionModel session = invocation.getArgument(0);
            session.setId(101L);
            return session;
        });
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(cashier));
        when(shiftRepository.findById(99L)).thenAnswer(invocation -> Optional.empty());
        when(highValueItemRepository.findBySessionIdOrderByIdAsc(anyLong())).thenReturn(List.of());
        when(approvalRepository.findBySessionIdOrderByDecidedAtDesc(anyLong())).thenReturn(List.of());

        ShiftSessionResponse response = service.getCurrent();

        assertEquals(ShiftSessionStatus.SCHEDULED, response.getStatus());
        assertEquals(99L, response.getShiftId());
    }

    @Test
    void startShiftRejectsMissingOpeningFundConfirmation() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        ShiftAssignmentModel assignment = publishedAssignment(cashier);
        stubCurrentAssignment(assignment);
        ShiftSessionModel scheduled = scheduledSession();
        when(sessionRepository.findFirstByShiftIdAndEmployeeIdOrderByIdDesc(SHIFT_ID, EMPLOYEE_ID))
                .thenReturn(Optional.of(scheduled));

        StartShiftRequest request = new StartShiftRequest();
        request.setConfirmedReceived(false);

        BusinessException error = assertThrows(BusinessException.class, () -> service.startShift(request));

        assertTrue(error.getMessage().contains(
                "You must confirm that you have received the opening fund before opening the shift."));
    }

    @Test
    void startShiftRejectsWrongSessionStatus() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        stubCurrentAssignment(publishedAssignment(cashier));
        ShiftSessionModel closing = scheduledSession();
        closing.setStatus(ShiftSessionStatus.CLOSING);
        when(sessionRepository.findFirstByShiftIdAndEmployeeIdOrderByIdDesc(SHIFT_ID, EMPLOYEE_ID))
                .thenReturn(Optional.of(closing));

        StartShiftRequest request = new StartShiftRequest();
        request.setConfirmedReceived(true);

        BusinessException error = assertThrows(BusinessException.class, () -> service.startShift(request));

        assertTrue(error.getMessage().contains("This shift is in closing."));
    }

    @Test
    void startShiftJoinsWhenColleagueOpenOnSameShift() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        stubCurrentAssignment(publishedAssignment(cashier));
        ShiftSessionModel scheduled = scheduledSession();
        when(sessionRepository.findFirstByShiftIdAndEmployeeIdOrderByIdDesc(SHIFT_ID, EMPLOYEE_ID))
                .thenReturn(Optional.of(scheduled));
        ShiftSessionModel otherOpen = openSession(ShiftSessionStatus.OPEN);
        otherOpen.setEmployeeId(99L);
        when(sessionRepository.findFirstByShiftIdAndStatusAndEmployeeIdNotOrderByOpenedAtAsc(
                SHIFT_ID, ShiftSessionStatus.OPEN, EMPLOYEE_ID))
                .thenReturn(Optional.of(otherOpen));
        when(sessionRepository.save(any(ShiftSessionModel.class))).thenAnswer(inv -> inv.getArgument(0));
        stubToResponseDeps(cashier);
        when(userRepository.findById(99L)).thenReturn(Optional.of(cashier(99L, "Other Cashier")));

        StartShiftRequest request = new StartShiftRequest();
        request.setConfirmedReceived(true);

        ShiftSessionResponse response = service.startShift(request);

        assertEquals(ShiftSessionStatus.OPEN, scheduled.getStatus());
        assertEquals(BigDecimal.ZERO, scheduled.getOpeningFundAmount());
        assertTrue(Boolean.TRUE.equals(response.getJoinedExistingShift()));
    }

    @Test
    void startShiftRejectsWhenAnotherCashierIsOpenOnDifferentShift() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        stubCurrentAssignment(publishedAssignment(cashier));
        ShiftSessionModel scheduled = scheduledSession();
        when(sessionRepository.findFirstByShiftIdAndEmployeeIdOrderByIdDesc(SHIFT_ID, EMPLOYEE_ID))
                .thenReturn(Optional.of(scheduled));
        when(sessionRepository.findFirstByShiftIdAndStatusAndEmployeeIdNotOrderByOpenedAtAsc(
                SHIFT_ID, ShiftSessionStatus.OPEN, EMPLOYEE_ID))
                .thenReturn(Optional.empty());
        ShiftSessionModel otherOpen = new ShiftSessionModel();
        otherOpen.setEmployeeId(99L);
        otherOpen.setShiftId(999L);
        otherOpen.setStatus(ShiftSessionStatus.OPEN);
        when(sessionRepository.findFirstByBranchIdAndStatusOrderByOpenedAtDesc(BRANCH_ID, ShiftSessionStatus.OPEN))
                .thenReturn(Optional.of(otherOpen));
        when(shiftRepository.findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                anyLong(), any(), any()))
                .thenReturn(List.of(publishedAssignment(cashier).getShift()));

        StartShiftRequest request = new StartShiftRequest();
        request.setConfirmedReceived(true);

        BusinessException error = assertThrows(BusinessException.class, () -> service.startShift(request));

        assertTrue(error.getMessage().contains(
                "Another cashier is currently operating an active shift in this branch."));
    }

    @Test
    void startShiftSucceedsWhenConfirmed() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        ShiftAssignmentModel assignment = publishedAssignment(cashier);
        stubCurrentAssignment(assignment);
        ShiftSessionModel scheduled = scheduledSession();
        when(sessionRepository.findFirstByShiftIdAndEmployeeIdOrderByIdDesc(SHIFT_ID, EMPLOYEE_ID))
                .thenReturn(Optional.of(scheduled));
        when(sessionRepository.findFirstByShiftIdAndStatusAndEmployeeIdNotOrderByOpenedAtAsc(
                SHIFT_ID, ShiftSessionStatus.OPEN, EMPLOYEE_ID))
                .thenReturn(Optional.empty());
        when(sessionRepository.findFirstByBranchIdAndStatusOrderByOpenedAtDesc(BRANCH_ID, ShiftSessionStatus.OPEN))
                .thenReturn(Optional.empty());
        when(shiftRepository.findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                anyLong(), any(), any()))
                .thenReturn(List.of(assignment.getShift()));
        when(sessionRepository.save(any(ShiftSessionModel.class))).thenAnswer(inv -> inv.getArgument(0));
        stubToResponseDeps(cashier);

        StartShiftRequest request = new StartShiftRequest();
        request.setConfirmedReceived(true);
        request.setNote("Fund received");

        ShiftSessionResponse response = service.startShift(request);

        assertEquals(ShiftSessionStatus.OPEN, scheduled.getStatus());
        assertNotNull(scheduled.getOpenedAt());
        assertEquals(ShiftSessionStatus.OPEN, response.getStatus());
    }

    @Test
    void startShiftStoresSelectedOpeningFundSourceAndMethod() {
        UserModel cashier = cashier();
        UserModel manager = branchManager(99L, "Minh Manager");
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        ShiftAssignmentModel assignment = publishedAssignment(cashier);
        stubCurrentAssignment(assignment);
        ShiftSessionModel scheduled = scheduledSession();
        when(sessionRepository.findFirstByShiftIdAndEmployeeIdOrderByIdDesc(SHIFT_ID, EMPLOYEE_ID))
                .thenReturn(Optional.of(scheduled));
        when(sessionRepository.findFirstByShiftIdAndStatusAndEmployeeIdNotOrderByOpenedAtAsc(
                SHIFT_ID, ShiftSessionStatus.OPEN, EMPLOYEE_ID))
                .thenReturn(Optional.empty());
        when(sessionRepository.findFirstByBranchIdAndStatusOrderByOpenedAtDesc(BRANCH_ID, ShiftSessionStatus.OPEN))
                .thenReturn(Optional.empty());
        when(shiftRepository.findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                anyLong(), any(), any()))
                .thenReturn(List.of(assignment.getShift()));
        when(userRepository.findActiveBranchManagers(BRANCH_ID)).thenReturn(List.of(manager));
        when(sessionRepository.save(any(ShiftSessionModel.class))).thenAnswer(inv -> inv.getArgument(0));
        stubToResponseDeps(cashier);
        when(userRepository.findById(99L)).thenReturn(Optional.of(manager));

        StartShiftRequest request = new StartShiftRequest();
        request.setConfirmedReceived(true);
        request.setReceivedFromEmployeeId(99L);
        request.setFundMethod(FundTransferMethod.TRANSFER);

        service.startShift(request);

        assertEquals(99L, scheduled.getOpeningFundReceivedFrom());
        assertEquals(FundTransferMethod.TRANSFER, scheduled.getOpeningFundMethod());
    }

    @Test
    void confirmOpeningFundRejectsNonScheduledSession() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        stubCurrentAssignment(publishedAssignment(cashier));
        ShiftSessionModel open = scheduledSession();
        open.setStatus(ShiftSessionStatus.OPEN);
        when(sessionRepository.findFirstByShiftIdAndEmployeeIdOrderByIdDesc(SHIFT_ID, EMPLOYEE_ID))
                .thenReturn(Optional.of(open));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.confirmOpeningFund(new ConfirmOpeningFundRequest()));

        assertTrue(error.getMessage().contains("This shift is already open."));
    }

    // -------------------------------------------------------------------------
    // closing / handover / verification
    // -------------------------------------------------------------------------

    @Test
    void confirmHandoverRejectsWithoutVerification() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        ShiftSessionModel closing = openSession(ShiftSessionStatus.CLOSING);
        closing.setVerificationConfirmed(false);
        when(sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(EMPLOYEE_ID), anyList()))
                .thenReturn(Optional.of(closing));

        ConfirmHandoverRequest request = new ConfirmHandoverRequest();
        request.setActualCash(new BigDecimal("2000000"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.confirmHandover(request));

        assertTrue(error.getMessage().contains("Complete high-value verification before handover."));
    }

    @Test
    void confirmHandoverRejectsNullActualCash() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        ShiftSessionModel closing = openSession(ShiftSessionStatus.CLOSING);
        closing.setVerificationConfirmed(true);
        when(sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(EMPLOYEE_ID), anyList()))
                .thenReturn(Optional.of(closing));
        when(paymentRepository.sumCashTakenInShift(SHIFT_ID)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.countTransactionsInShift(SHIFT_ID)).thenReturn(0L);

        ConfirmHandoverRequest request = new ConfirmHandoverRequest();
        request.setActualCash(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.confirmHandover(request));

        assertTrue(error.getMessage().contains("Actual cash is required."));
    }

    @Test
    void confirmHandoverRejectsDifferenceWithoutRemark() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        ShiftSessionModel closing = openSession(ShiftSessionStatus.CLOSING);
        closing.setVerificationConfirmed(true);
        closing.setOpeningFundAmount(new BigDecimal("2000000"));
        when(sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(EMPLOYEE_ID), anyList()))
                .thenReturn(Optional.of(closing));
        when(paymentRepository.sumCashTakenInShift(SHIFT_ID)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.countTransactionsInShift(SHIFT_ID)).thenReturn(0L);

        ConfirmHandoverRequest request = new ConfirmHandoverRequest();
        request.setActualCash(new BigDecimal("1900000"));
        request.setRemark("  ");

        BusinessException error = assertThrows(BusinessException.class, () -> service.confirmHandover(request));

        assertTrue(error.getMessage().contains("Remark is required when there is a cash difference."));
    }

    @Test
    void confirmHandoverStoresCashierSelectedScheduledReplacement() {
        UserModel cashier = cashier();
        UserModel replacement = cashier(22L, "Mai Replacement");
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        ShiftSessionModel closing = openSession(ShiftSessionStatus.CLOSING);
        closing.setVerificationConfirmed(true);
        closing.setOpeningFundAmount(new BigDecimal("2000000"));
        when(sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(EMPLOYEE_ID), anyList()))
                .thenReturn(Optional.of(closing));
        when(paymentRepository.sumCashTakenInShift(SHIFT_ID)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.countTransactionsInShift(SHIFT_ID)).thenReturn(0L);
        ShiftModel current = shiftModel();
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(current));
        when(shiftRepository.findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                anyLong(), any(), any())).thenReturn(List.of(current));
        ShiftAssignmentModel replacementAssignment = publishedAssignment(replacement);
        replacementAssignment.setShift(current);
        when(assignmentRepository.findByShiftId(SHIFT_ID)).thenReturn(List.of(replacementAssignment));
        when(userRepository.findActiveBranchManagers(BRANCH_ID)).thenReturn(List.of());
        when(sessionRepository.save(any(ShiftSessionModel.class))).thenAnswer(inv -> inv.getArgument(0));
        stubToResponseDeps(cashier);
        when(userRepository.findById(22L)).thenReturn(Optional.of(replacement));

        ConfirmHandoverRequest request = new ConfirmHandoverRequest();
        request.setActualCash(new BigDecimal("2000000"));
        request.setHandoverToEmployeeId(22L);

        service.confirmHandover(request);

        assertEquals(22L, closing.getHandoverToEmployeeId());
        assertTrue(closing.getHandoverConfirmed());
    }

    @Test
    void confirmHandoverRejectsEarlyCloseWithoutScheduledReplacement() {
        UserModel cashier = cashier();
        UserModel manager = branchManager(99L, "Minh Manager");
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        ShiftSessionModel closing = openSession(ShiftSessionStatus.CLOSING);
        closing.setVerificationConfirmed(true);
        closing.setOpeningFundAmount(new BigDecimal("2000000"));
        when(sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(EMPLOYEE_ID), anyList()))
                .thenReturn(Optional.of(closing));
        when(paymentRepository.sumCashTakenInShift(SHIFT_ID)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.countTransactionsInShift(SHIFT_ID)).thenReturn(0L);
        ShiftModel current = shiftModel();
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(current));
        when(shiftRepository.findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                anyLong(), any(), any())).thenReturn(List.of(current));
        when(assignmentRepository.findByShiftId(SHIFT_ID)).thenReturn(List.of());
        when(userRepository.findActiveBranchManagers(BRANCH_ID)).thenReturn(List.of(manager));

        ConfirmHandoverRequest request = new ConfirmHandoverRequest();
        request.setActualCash(new BigDecimal("2000000"));
        request.setHandoverToEmployeeId(99L);

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.confirmHandover(request));

        assertTrue(error.getMessage().contains("Early closing requires a scheduled replacement"));
    }

    @Test
    void confirmVerificationRejectsUnknownProduct() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        ShiftSessionModel closing = openSession(ShiftSessionStatus.CLOSING);
        when(sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(EMPLOYEE_ID), anyList()))
                .thenReturn(Optional.of(closing));
        when(highValueItemRepository.findBySessionIdOrderByIdAsc(SESSION_ID)).thenReturn(List.of());

        ConfirmVerificationRequest request = new ConfirmVerificationRequest();
        ConfirmVerificationRequest.HighValueLineRequest line =
                new ConfirmVerificationRequest.HighValueLineRequest();
        line.setProductId(101);
        line.setActualQty(1);
        request.setItems(List.of(line));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.confirmVerification(request));

        assertTrue(error.getMessage().contains("Unknown high-value product in verification."));
    }

    @Test
    void closeCashierShiftRejectsIncompleteSteps() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        ShiftSessionModel closing = openSession(ShiftSessionStatus.CLOSING);
        closing.setVerificationConfirmed(true);
        closing.setHandoverConfirmed(false);
        when(sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(EMPLOYEE_ID), anyList()))
                .thenReturn(Optional.of(closing));

        BusinessException error = assertThrows(BusinessException.class, () -> service.closeCashierShift());

        assertTrue(error.getMessage().contains(
                "Complete verification and handover before closing the shift."));
    }

    @Test
    void closeCashierShiftCompletesWhenBalanced() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        ShiftSessionModel closing = openSession(ShiftSessionStatus.CLOSING);
        closing.setVerificationConfirmed(true);
        closing.setHandoverConfirmed(true);
        closing.setOpeningFundAmount(new BigDecimal("2000000"));
        closing.setActualCash(new BigDecimal("2000000"));
        when(sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(EMPLOYEE_ID), anyList()))
                .thenReturn(Optional.of(closing));
        when(paymentRepository.sumCashTakenInShift(SHIFT_ID)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.countTransactionsInShift(SHIFT_ID)).thenReturn(0L);
        when(sessionRepository.save(any(ShiftSessionModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(shiftModel()));
        when(shiftRepository.save(any(ShiftModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findFirstByShiftIdAndStaffIdOrderByIdDesc(SHIFT_ID, EMPLOYEE_ID))
                .thenReturn(Optional.of(publishedAssignment(cashier)));
        stubToResponseDeps(cashier);

        ShiftSessionResponse response = service.closeCashierShift();

        assertEquals(ShiftSessionStatus.COMPLETED, closing.getStatus());
        assertEquals(ShiftSessionStatus.COMPLETED, response.getStatus());
    }

    @Test
    void closeCashierShiftPendingApprovalWhenDifference() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        ShiftSessionModel closing = openSession(ShiftSessionStatus.CLOSING);
        closing.setVerificationConfirmed(true);
        closing.setHandoverConfirmed(true);
        closing.setOpeningFundAmount(new BigDecimal("2000000"));
        closing.setActualCash(new BigDecimal("1900000"));
        closing.setHandoverRemark("Short drawer");
        when(sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(EMPLOYEE_ID), anyList()))
                .thenReturn(Optional.of(closing));
        when(paymentRepository.sumCashTakenInShift(SHIFT_ID)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.countTransactionsInShift(SHIFT_ID)).thenReturn(0L);
        when(sessionRepository.save(any(ShiftSessionModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(shiftModel()));
        when(shiftRepository.save(any(ShiftModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findFirstByShiftIdAndStaffIdOrderByIdDesc(SHIFT_ID, EMPLOYEE_ID))
                .thenReturn(Optional.empty());
        stubToResponseDeps(cashier);

        service.closeCashierShift();

        assertEquals(ShiftSessionStatus.PENDING_APPROVAL, closing.getStatus());
    }

    @Test
    void closeCashierShiftPendingApprovalWhenProductDifferenceOnly() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        ShiftSessionModel closing = openSession(ShiftSessionStatus.CLOSING);
        closing.setVerificationConfirmed(true);
        closing.setHandoverConfirmed(true);
        closing.setOpeningFundAmount(new BigDecimal("2000000"));
        closing.setActualCash(new BigDecimal("2000000"));
        when(sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(EMPLOYEE_ID), anyList()))
                .thenReturn(Optional.of(closing));
        when(paymentRepository.sumCashTakenInShift(SHIFT_ID)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.countTransactionsInShift(SHIFT_ID)).thenReturn(0L);
        when(sessionRepository.save(any(ShiftSessionModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(shiftModel()));
        when(shiftRepository.save(any(ShiftModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findFirstByShiftIdAndStaffIdOrderByIdDesc(SHIFT_ID, EMPLOYEE_ID))
                .thenReturn(Optional.empty());
        stubToResponseDeps(cashier);
        ShiftSessionHighValueItemModel hvItem = new ShiftSessionHighValueItemModel();
        hvItem.setSessionId(SESSION_ID);
        hvItem.setProductId(101);
        hvItem.setExpectedQty(5);
        hvItem.setActualQty(4);
        hvItem.setDifference(-1);
        when(highValueItemRepository.findBySessionIdOrderByIdAsc(anyLong())).thenReturn(List.of(hvItem));

        service.closeCashierShift();

        assertEquals(ShiftSessionStatus.PENDING_APPROVAL, closing.getStatus());
    }

    @Test
    void getCurrentAutoJoinsWhenColleagueOpenSameShift() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        when(sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(EMPLOYEE_ID), anyList()))
                .thenReturn(Optional.empty());
        ShiftAssignmentModel assignment = publishedAssignment(cashier);
        when(assignmentRepository.findPublishedAssignmentsOverlapping(
                eq(EMPLOYEE_ID), any(), any(), eq(ShiftStatus.PUBLISHED)))
                .thenReturn(List.of(assignment));
        ShiftSessionModel scheduled = scheduledSession();
        when(sessionRepository.findFirstByShiftIdAndEmployeeIdOrderByIdDesc(SHIFT_ID, EMPLOYEE_ID))
                .thenReturn(Optional.of(scheduled));
        when(sessionRepository.save(any(ShiftSessionModel.class))).thenAnswer(inv -> inv.getArgument(0));
        ShiftSessionModel colleagueOpen = openSession(ShiftSessionStatus.OPEN);
        colleagueOpen.setEmployeeId(99L);
        when(sessionRepository.findFirstByShiftIdAndStatusAndEmployeeIdNotOrderByOpenedAtAsc(
                SHIFT_ID, ShiftSessionStatus.OPEN, EMPLOYEE_ID))
                .thenReturn(Optional.of(colleagueOpen));
        stubToResponseDeps(cashier);
        when(userRepository.findById(99L)).thenReturn(Optional.of(cashier(99L, "Colleague")));

        ShiftSessionResponse response = service.getCurrent();

        assertEquals(ShiftSessionStatus.OPEN, scheduled.getStatus());
        assertTrue(Boolean.TRUE.equals(response.getJoinedExistingShift()));
    }

    @Test
    void saveClosingDraftPersistsCashAndNotes() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        ShiftSessionModel closing = openSession(ShiftSessionStatus.CLOSING);
        closing.setOpeningFundAmount(new BigDecimal("2000000"));
        when(sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(EMPLOYEE_ID), anyList()))
                .thenReturn(Optional.of(closing));
        when(paymentRepository.sumCashTakenInShift(SHIFT_ID)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.countTransactionsInShift(SHIFT_ID)).thenReturn(0L);
        when(sessionRepository.save(any(ShiftSessionModel.class))).thenAnswer(inv -> inv.getArgument(0));
        stubToResponseDeps(cashier);

        SaveClosingDraftRequest request = new SaveClosingDraftRequest();
        request.setActualCash(new BigDecimal("2100000"));
        request.setClosingNote("Draft note");
        request.setHandoverRemark("ok");

        service.saveClosingDraft(request);

        assertEquals(new BigDecimal("2100000"), closing.getActualCash());
        assertEquals("Draft note", closing.getClosingNote());
        verify(sessionRepository).save(closing);
    }

    // -------------------------------------------------------------------------
    // list / history / inventory close guards
    // -------------------------------------------------------------------------

    @Test
    void listBranchSessionsForManagerRejectsNonManager() {
        signedInAs(UserRole.CASHIER);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.CASHIER);

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.listBranchSessionsForManager());

        assertTrue(error.getMessage().contains("Only branch managers can approve cash discrepancies."));
    }

    @Test
    void listPendingReconciliationRejectsManagerWithoutBranch() {
        UserModel manager = new UserModel();
        manager.setId(99L);
        manager.setRole(UserRole.BRANCH_MANAGER);
        manager.setBranchId(null);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(manager);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.BRANCH_MANAGER);

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.listPendingReconciliation());

        assertTrue(error.getMessage().contains("Branch manager is not assigned to a branch."));
    }

    @Test
    void getHistoryRejectsNonCashier() {
        signedInAs(UserRole.INVENTORY_STAFF);

        BusinessException error = assertThrows(BusinessException.class, () -> service.getHistory());

        assertTrue(error.getMessage().contains("Only cashiers can use shift sessions."));
    }

    @Test
    void closeInventoryShiftRejectsBecauseInventoryPathDisabled() {
        signedInAs(UserRole.CASHIER);

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.closeInventoryShift(new CloseInventoryShiftRequest()));

        assertTrue(error.getMessage().contains("This action is for inventory staff only."));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void getClosingContextMovesOpenSessionToClosing() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        ShiftSessionModel open = openSession(ShiftSessionStatus.OPEN);
        open.setOpeningFundAmount(new BigDecimal("2000000"));
        when(sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(EMPLOYEE_ID), anyList()))
                .thenReturn(Optional.of(open));
        when(paymentRepository.sumCashTakenInShift(SHIFT_ID)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.countTransactionsInShift(SHIFT_ID)).thenReturn(0L);
        when(highValueItemRepository.findBySessionIdOrderByIdAsc(SESSION_ID)).thenReturn(List.of());
        when(branchInventoryRepository.findByBranchId(BRANCH_ID)).thenReturn(List.of());
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(shiftModel()));
        when(shiftRepository.findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                anyLong(), any(), any()))
                .thenReturn(List.of(shiftModel()));
        when(sessionRepository.save(any(ShiftSessionModel.class))).thenAnswer(inv -> inv.getArgument(0));
        stubToResponseDeps(cashier);

        ShiftSessionResponse response = service.getClosingContext();

        assertEquals(ShiftSessionStatus.CLOSING, open.getStatus());
        assertEquals(ShiftSessionStatus.CLOSING, response.getStatus());
    }

    @Test
    void startShiftReturnsExistingOpenSessionIdempotently() {
        UserModel cashier = cashier();
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        stubCurrentAssignment(publishedAssignment(cashier));
        ShiftSessionModel open = scheduledSession();
        open.setStatus(ShiftSessionStatus.OPEN);
        when(sessionRepository.findFirstByShiftIdAndEmployeeIdOrderByIdDesc(SHIFT_ID, EMPLOYEE_ID))
                .thenReturn(Optional.of(open));
        stubToResponseDeps(cashier);

        ShiftSessionResponse response = service.startShift(new StartShiftRequest());

        assertEquals(ShiftSessionStatus.OPEN, response.getStatus());
        verify(sessionRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private void signedInAs(UserRole role) {
        UserModel user = new UserModel();
        user.setId(EMPLOYEE_ID);
        user.setBranchId(BRANCH_ID);
        user.setRole(role);
        user.setUserName("user");
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(user);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(role);
    }

    private UserModel cashier() {
        return cashier(EMPLOYEE_ID, "Lan Nguyen");
    }

    private UserModel cashier(Long id, String name) {
        UserModel user = new UserModel();
        user.setId(id);
        user.setBranchId(BRANCH_ID);
        user.setRole(UserRole.CASHIER);
        user.setUserName("cashier" + id);
        user.setFullName(name);
        user.setEmail("cashier" + id + "@chainstore.com");
        return user;
    }

    private UserModel branchManager(Long id, String name) {
        UserModel user = new UserModel();
        user.setId(id);
        user.setBranchId(BRANCH_ID);
        user.setRole(UserRole.BRANCH_MANAGER);
        user.setUserName("manager" + id);
        user.setFullName(name);
        user.setEmail("manager" + id + "@chainstore.com");
        return user;
    }

    private ShiftModel shiftModel() {
        ShiftModel shift = new ShiftModel();
        shift.setId(SHIFT_ID);
        shift.setBranchId(BRANCH_ID);
        shift.setCreatedBy(99L);
        shift.setStartTime(LocalDateTime.now().minusHours(1));
        shift.setEndTime(LocalDateTime.now().plusHours(3));
        shift.setOpeningCash(new BigDecimal("2000000"));
        shift.setStatus(ShiftStatus.PUBLISHED);
        return shift;
    }

    private ShiftAssignmentModel publishedAssignment(UserModel staff) {
        ShiftAssignmentModel assignment = new ShiftAssignmentModel();
        assignment.setId(ASSIGNMENT_ID);
        assignment.setShift(shiftModel());
        assignment.setStaff(staff);
        assignment.setAssignedRole(UserRole.CASHIER);
        assignment.setCheckInAt(LocalDateTime.now().minusMinutes(10));
        return assignment;
    }

    private void stubCurrentAssignment(ShiftAssignmentModel assignment) {
        when(assignmentRepository.findPublishedAssignmentsOverlapping(
                eq(EMPLOYEE_ID), any(), any(), eq(ShiftStatus.PUBLISHED)))
                .thenReturn(List.of(assignment));
        when(assignmentRepository.save(any(ShiftAssignmentModel.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ShiftSessionModel scheduledSession() {
        ShiftSessionModel session = new ShiftSessionModel();
        session.setId(SESSION_ID);
        session.setShiftId(SHIFT_ID);
        session.setShiftAssignmentId(ASSIGNMENT_ID);
        session.setEmployeeId(EMPLOYEE_ID);
        session.setBranchId(BRANCH_ID);
        session.setRole(UserRole.CASHIER);
        session.setStatus(ShiftSessionStatus.SCHEDULED);
        session.setOpeningConfirmed(false);
        return session;
    }

    private ShiftSessionModel openSession(ShiftSessionStatus status) {
        ShiftSessionModel session = scheduledSession();
        session.setStatus(status);
        session.setOpenedAt(LocalDateTime.now().minusHours(1));
        session.setOpeningConfirmed(true);
        return session;
    }

    private void stubToResponseDeps(UserModel cashier) {
        when(userRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(cashier));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.empty());
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(shiftModel()));
        when(shiftRepository.findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                anyLong(), any(), any()))
                .thenReturn(List.of(shiftModel()));
        when(assignmentRepository.findFirstByShiftIdAndStaffIdOrderByIdDesc(SHIFT_ID, EMPLOYEE_ID))
                .thenReturn(Optional.of(publishedAssignment(cashier)));
        when(highValueItemRepository.findBySessionIdOrderByIdAsc(anyLong())).thenReturn(List.of());
        when(approvalRepository.findBySessionIdOrderByDecidedAtDesc(anyLong())).thenReturn(List.of());
    }
}
