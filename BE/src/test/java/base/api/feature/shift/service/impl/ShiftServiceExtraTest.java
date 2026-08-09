package base.api.feature.shift.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.shift.dto.request.AssignEmployeesRequest;
import base.api.feature.shift.dto.request.CreateShiftRequest;
import base.api.feature.shift.dto.request.ReplaceAssignedEmployeeRequest;
import base.api.feature.shift.dto.request.UpdateShiftRequest;
import base.api.feature.shift.dto.request.WeekScheduleRequest;
import base.api.feature.shift.dto.response.AvailableEmployeeResponse;
import base.api.feature.shift.dto.response.PublishWeekResponse;
import base.api.feature.shift.dto.response.ShiftResponse;
import base.api.feature.shift.dto.response.WeeklyScheduleResponse;
import base.api.feature.shift.mapper.ShiftMapper;
import base.api.feature.shift.repository.ShiftAssignmentRepository;
import base.api.feature.shift.repository.ShiftRepository;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.ShiftAssignmentModel;
import base.api.shared.entity.ShiftModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.ShiftStatus;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BusinessException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Extra ShiftServiceImpl coverage beyond {@link ShiftOpeningCashTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShiftServiceExtraTest {

    private static final Long BRANCH_ID = 10L;
    private static final BigDecimal DEFAULT_FLOAT = new BigDecimal("2000000");

    @Mock private ShiftRepository shiftRepository;
    @Mock private ShiftAssignmentRepository assignmentRepository;
    @Mock private IUserRepository userRepository;
    @Mock private IBranchRepository branchRepository;
    @Mock private ShiftMapper shiftMapper;
    @Mock private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private ShiftServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "defaultOpeningCash", DEFAULT_FLOAT);
    }

    // -------------------------------------------------------------------------
    // create / update / delete
    // -------------------------------------------------------------------------

    @Test
    void createRejectsNonBranchManager() {
        signedInAs(UserRole.CASHIER, BRANCH_ID);

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(createRequest()));

        assertTrue(error.getMessage().contains("Only branch managers can manage shifts."));
    }

    @Test
    void createRejectsOtherBranch() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        CreateShiftRequest request = createRequest();
        request.setBranchId(99L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(request));

        assertTrue(error.getMessage().contains(
                "Branch managers can only manage shifts in their own branch."));
    }

    @Test
    void createRejectsOverlappingShift() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(branchRepository.existsById(BRANCH_ID)).thenReturn(true);
        when(shiftRepository.existsOverlapping(eq(BRANCH_ID), any(), any(), isNull())).thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(createRequest()));

        assertTrue(error.getMessage().contains("Shift already exists in this time range."));
    }

    @Test
    void createRejectsNegativeOpeningCash() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(branchRepository.existsById(BRANCH_ID)).thenReturn(true);
        when(shiftRepository.existsOverlapping(anyLong(), any(), any(), any())).thenReturn(false);
        CreateShiftRequest request = createRequest();
        request.setOpeningCash(new BigDecimal("-1"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(request));

        assertTrue(error.getMessage().contains("Opening cash must be greater than or equal to 0."));
    }

    @Test
    void createRejectsEndBeforeStart() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(branchRepository.existsById(BRANCH_ID)).thenReturn(true);
        CreateShiftRequest request = createRequest();
        request.setStartTime(LocalDateTime.of(2026, 7, 27, 12, 0));
        request.setEndTime(LocalDateTime.of(2026, 7, 27, 8, 0));

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(request));

        assertTrue(error.getMessage().contains("End time must be after start time."));
    }

    @Test
    void updateRejectsNonDraftShift() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        ShiftModel published = shift(1L, ShiftStatus.PUBLISHED);
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(published));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.update(1L, updateRequest()));

        assertTrue(error.getMessage().contains("Shift cannot be modified once it is no longer DRAFT."));
    }

    @Test
    void updateSucceedsForDraftShift() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        ShiftModel draft = shift(1L, ShiftStatus.DRAFT);
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(shiftRepository.existsOverlapping(eq(BRANCH_ID), any(), any(), eq(1L))).thenReturn(false);
        when(shiftRepository.save(any(ShiftModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findByShiftId(1L)).thenReturn(List.of());
        when(shiftMapper.toResponse(any(), any())).thenReturn(new ShiftResponse());

        service.update(1L, updateRequest());

        verify(shiftRepository).save(draft);
    }

    @Test
    void deleteRejectsNonDraftShift() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(shift(1L, ShiftStatus.PUBLISHED)));

        BusinessException error = assertThrows(BusinessException.class, () -> service.delete(1L));

        assertTrue(error.getMessage().contains("Shift cannot be modified once it is no longer DRAFT."));
        verify(shiftRepository, never()).delete(any());
    }

    @Test
    void deleteSucceedsForDraftShift() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        ShiftModel draft = shift(1L, ShiftStatus.DRAFT);
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(draft));

        service.delete(1L);

        verify(assignmentRepository).deleteAllByShiftId(1L);
        verify(shiftRepository).delete(draft);
    }

    // -------------------------------------------------------------------------
    // assign / remove / replace / publish
    // -------------------------------------------------------------------------

    @Test
    void assignEmployeesRejectsAlreadyAssigned() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        ShiftModel draft = shift(1L, ShiftStatus.DRAFT);
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(draft));
        UserModel cashier = employee(20L, UserRole.CASHIER);
        ShiftAssignmentModel existing = assignment(draft, cashier);
        when(assignmentRepository.findByShiftId(1L)).thenReturn(List.of(existing));

        AssignEmployeesRequest request = new AssignEmployeesRequest();
        request.setEmployeeIds(List.of(20L));
        request.setRequiredRole(UserRole.CASHIER);

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.assignEmployees(1L, request));

        assertTrue(error.getMessage().contains("Employee is already assigned to this shift."));
    }

    @Test
    void assignEmployeesRejectsMissingEmployee() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(shift(1L, ShiftStatus.DRAFT)));
        when(assignmentRepository.findByShiftId(1L)).thenReturn(List.of());
        when(userRepository.findAllById(List.of(20L))).thenReturn(List.of());

        AssignEmployeesRequest request = new AssignEmployeesRequest();
        request.setEmployeeIds(List.of(20L));
        request.setRequiredRole(UserRole.CASHIER);

        NotFoundException error = assertThrows(
                NotFoundException.class, () -> service.assignEmployees(1L, request));

        assertTrue(error.getMessage().contains("One or more selected employees were not found."));
    }

    @Test
    void assignEmployeesRejectsWrongBranchEmployee() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(shift(1L, ShiftStatus.DRAFT)));
        when(assignmentRepository.findByShiftId(1L)).thenReturn(List.of());
        UserModel otherBranch = employee(20L, UserRole.CASHIER);
        otherBranch.setBranchId(99L);
        when(userRepository.findAllById(List.of(20L))).thenReturn(List.of(otherBranch));

        AssignEmployeesRequest request = new AssignEmployeesRequest();
        request.setEmployeeIds(List.of(20L));
        request.setRequiredRole(UserRole.CASHIER);

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.assignEmployees(1L, request));

        assertTrue(error.getMessage().contains("Employee must belong to the same branch as the shift."));
    }

    @Test
    void assignEmployeesSucceeds() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        ShiftModel draft = shift(1L, ShiftStatus.DRAFT);
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(assignmentRepository.findByShiftId(1L)).thenReturn(List.of());
        UserModel cashier = employee(20L, UserRole.CASHIER);
        when(userRepository.findAllById(List.of(20L))).thenReturn(List.of(cashier));
        when(assignmentRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(shiftMapper.toResponse(any(), any())).thenReturn(new ShiftResponse());

        AssignEmployeesRequest request = new AssignEmployeesRequest();
        request.setEmployeeIds(List.of(20L));
        request.setRequiredRole(UserRole.CASHIER);

        service.assignEmployees(1L, request);

        verify(assignmentRepository).saveAll(any());
    }

    @Test
    void removeEmployeeRejectsMissingAssignment() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(shift(1L, ShiftStatus.DRAFT)));
        when(assignmentRepository.findByShiftIdAndStaffId(1L, 20L)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(
                NotFoundException.class, () -> service.removeEmployee(1L, 20L));

        assertTrue(error.getMessage().contains("Shift assignment not found."));
    }

    @Test
    void replaceEmployeeRejectsAlreadyAssignedReplacement() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        ShiftModel draft = shift(1L, ShiftStatus.DRAFT);
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(draft));
        UserModel current = employee(20L, UserRole.CASHIER);
        UserModel replacement = employee(21L, UserRole.CASHIER);
        when(assignmentRepository.findByShiftIdAndStaffId(1L, 20L))
                .thenReturn(Optional.of(assignment(draft, current)));
        when(userRepository.findById(21L)).thenReturn(Optional.of(replacement));
        when(assignmentRepository.existsByShiftIdAndStaffId(1L, 21L)).thenReturn(true);

        ReplaceAssignedEmployeeRequest request = new ReplaceAssignedEmployeeRequest();
        request.setReplacementEmployeeId(21L);
        request.setRequiredRole(UserRole.CASHIER);

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.replaceEmployee(1L, 20L, request));

        assertTrue(error.getMessage().contains("Replacement employee is already assigned to this shift."));
    }

    @Test
    void publishRejectsEmptyAssignments() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        ShiftModel draft = shift(1L, ShiftStatus.DRAFT);
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(assignmentRepository.findByShiftId(1L)).thenReturn(List.of());
        when(shiftRepository.findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                eq(BRANCH_ID), any(), any()))
                .thenReturn(List.of(draft));

        BusinessException error = assertThrows(BusinessException.class, () -> service.publish(1L));

        assertTrue(error.getMessage().contains(
                "Every shift needs at least one employee before publishing."));
    }

    @Test
    void publishRejectsMissingCashier() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        ShiftModel draft = shift(1L, ShiftStatus.DRAFT);
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(draft));
        UserModel is = employee(30L, UserRole.INVENTORY_STAFF);
        ShiftAssignmentModel onlyIs = assignment(draft, is);
        onlyIs.setAssignedRole(UserRole.INVENTORY_STAFF);
        when(assignmentRepository.findByShiftId(1L)).thenReturn(List.of(onlyIs));
        when(shiftRepository.findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                eq(BRANCH_ID), any(), any()))
                .thenReturn(List.of(draft));

        BusinessException error = assertThrows(BusinessException.class, () -> service.publish(1L));

        assertTrue(error.getMessage().contains(
                "Every shift needs at least one Cashier before publishing."));
    }

    @Test
    void publishSucceedsWithCashierAndInventoryOnFirstShift() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        ShiftModel draft = shift(1L, ShiftStatus.DRAFT);
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(draft));
        UserModel cashier = employee(20L, UserRole.CASHIER);
        UserModel is = employee(30L, UserRole.INVENTORY_STAFF);
        ShiftAssignmentModel a1 = assignment(draft, cashier);
        a1.setAssignedRole(UserRole.CASHIER);
        ShiftAssignmentModel a2 = assignment(draft, is);
        a2.setAssignedRole(UserRole.INVENTORY_STAFF);
        when(assignmentRepository.findByShiftId(1L)).thenReturn(List.of(a1, a2));
        when(shiftRepository.findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                eq(BRANCH_ID), any(), any()))
                .thenReturn(List.of(draft));
        when(shiftRepository.save(any(ShiftModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shiftMapper.toResponse(any(), any())).thenReturn(new ShiftResponse());

        service.publish(1L);

        assertEquals(ShiftStatus.PUBLISHED, draft.getStatus());
        verify(shiftRepository).save(draft);
    }

    // -------------------------------------------------------------------------
    // my shifts / check-in / availability / list / publish week
    // -------------------------------------------------------------------------

    @Test
    void getMyShiftsRejectsBranchManager() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);

        BusinessException error = assertThrows(BusinessException.class, () -> service.getMyShifts());

        assertTrue(error.getMessage().contains(
                "Only cashiers and inventory staff can view assigned shifts."));
    }

    @Test
    void getMyWeeklyScheduleRejectsBranchManager() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.getMyWeeklySchedule(LocalDate.of(2026, 7, 27)));

        assertTrue(error.getMessage().contains(
                "Only cashiers and inventory staff can view assigned shifts."));
    }

    @Test
    void getMyWeeklyScheduleReturnsPublishedAssignmentsForWeek() {
        UserModel cashier = employee(20L, UserRole.CASHIER);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);

        LocalDate weekStart = LocalDate.of(2026, 7, 27); // Monday
        ShiftModel published = shift(1L, ShiftStatus.PUBLISHED);
        published.setStartTime(LocalDateTime.of(2026, 7, 28, 8, 0));
        published.setEndTime(LocalDateTime.of(2026, 7, 28, 12, 0));
        ShiftAssignmentModel myAssignment = assignment(published, cashier);

        when(assignmentRepository.findPublishedAssignmentsForStaffBetween(
                eq(20L),
                eq(weekStart.atStartOfDay()),
                eq(weekStart.plusDays(7).atStartOfDay()),
                eq(ShiftStatus.PUBLISHED)))
                .thenReturn(List.of(myAssignment));
        when(assignmentRepository.findByShiftIdIn(any())).thenReturn(List.of(myAssignment));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(BRANCH_ID)));
        when(shiftMapper.toResponse(eq(published), any())).thenAnswer(inv -> {
            ShiftResponse response = new ShiftResponse();
            response.setId(1L);
            response.setStartTime(published.getStartTime());
            response.setEndTime(published.getEndTime());
            response.setStatus(ShiftStatus.PUBLISHED);
            return response;
        });

        WeeklyScheduleResponse result = service.getMyWeeklySchedule(LocalDate.of(2026, 7, 29));

        assertEquals(BRANCH_ID, result.getBranchId());
        assertEquals(weekStart, result.getWeekStart());
        assertEquals(7, result.getDays().size());
        long shiftsInWeek = result.getDays().stream()
                .mapToLong(day -> day.getShifts() == null ? 0 : day.getShifts().size())
                .sum();
        assertEquals(1, shiftsInWeek);
    }

    @Test
    void checkInRejectsNonPublishedShift() {
        UserModel cashier = employee(20L, UserRole.CASHIER);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(shift(1L, ShiftStatus.DRAFT)));

        BusinessException error = assertThrows(BusinessException.class, () -> service.checkIn(1L));

        assertTrue(error.getMessage().contains("Only published shifts can be checked in."));
    }

    @Test
    void checkInRejectsUnassignedStaff() {
        UserModel cashier = employee(20L, UserRole.CASHIER);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(shift(1L, ShiftStatus.PUBLISHED)));
        when(assignmentRepository.findFirstByShiftIdAndStaffIdOrderByIdDesc(1L, 20L))
                .thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class, () -> service.checkIn(1L));

        assertTrue(error.getMessage().contains("You are not assigned to this shift."));
    }

    @Test
    void findAvailableEmployeesRejectsMissingDate() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(branchRepository.existsById(BRANCH_ID)).thenReturn(true);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.findAvailableEmployees(
                        BRANCH_ID, null, LocalTime.of(8, 0), LocalTime.of(12, 0), UserRole.CASHIER));

        assertTrue(error.getMessage().contains("Date is required."));
    }

    @Test
    void getAllRejectsOtherBranch() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);

        BusinessException error = assertThrows(BusinessException.class, () -> service.getAll(99L));

        assertTrue(error.getMessage().contains(
                "Branch managers can only manage shifts in their own branch."));
    }

    @Test
    void publishWeekPublishesValidDrafts() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(branchRepository.existsById(BRANCH_ID)).thenReturn(true);
        ShiftModel draft = shift(1L, ShiftStatus.DRAFT);
        draft.setStartTime(LocalDateTime.of(2026, 7, 27, 8, 0));
        draft.setEndTime(LocalDateTime.of(2026, 7, 27, 12, 0));
        UserModel cashier = employee(20L, UserRole.CASHIER);
        UserModel is = employee(30L, UserRole.INVENTORY_STAFF);
        ShiftAssignmentModel a1 = assignment(draft, cashier);
        a1.setAssignedRole(UserRole.CASHIER);
        ShiftAssignmentModel a2 = assignment(draft, is);
        a2.setAssignedRole(UserRole.INVENTORY_STAFF);
        when(shiftRepository.findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                eq(BRANCH_ID), any(), any()))
                .thenReturn(List.of(draft));
        when(assignmentRepository.findByShiftIdIn(List.of(1L))).thenReturn(List.of(a1, a2));
        when(shiftMapper.toResponse(any(), any())).thenReturn(new ShiftResponse());

        WeekScheduleRequest request = new WeekScheduleRequest();
        request.setBranchId(BRANCH_ID);
        request.setWeekStart(LocalDate.of(2026, 7, 27));

        PublishWeekResponse response = service.publishWeek(request);

        assertEquals(1, response.getPublished());
        assertEquals(ShiftStatus.PUBLISHED, draft.getStatus());
    }

    @Test
    void getByIdRejectsMissingShift() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(shiftRepository.findById(99L)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class, () -> service.getById(99L));

        assertTrue(error.getMessage().contains("Shift not found."));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private void signedInAs(UserRole role, Long branchId) {
        UserModel user = new UserModel();
        user.setId(5L);
        user.setBranchId(branchId);
        user.setRole(role);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(user);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(role);
    }

    private CreateShiftRequest createRequest() {
        CreateShiftRequest request = new CreateShiftRequest();
        request.setBranchId(BRANCH_ID);
        request.setStartTime(LocalDateTime.of(2026, 7, 27, 8, 0));
        request.setEndTime(LocalDateTime.of(2026, 7, 27, 12, 0));
        return request;
    }

    private UpdateShiftRequest updateRequest() {
        UpdateShiftRequest request = new UpdateShiftRequest();
        request.setStartTime(LocalDateTime.of(2026, 7, 27, 9, 0));
        request.setEndTime(LocalDateTime.of(2026, 7, 27, 13, 0));
        request.setOpeningCash(BigDecimal.ZERO);
        request.setExpectedCash(BigDecimal.ZERO);
        return request;
    }

    private ShiftModel shift(Long id, ShiftStatus status) {
        ShiftModel shift = new ShiftModel();
        shift.setId(id);
        shift.setBranchId(BRANCH_ID);
        shift.setStartTime(LocalDateTime.of(2026, 7, 27, 8, 0));
        shift.setEndTime(LocalDateTime.of(2026, 7, 27, 12, 0));
        shift.setExpectedCash(BigDecimal.ZERO);
        shift.setStatus(status);
        return shift;
    }

    private UserModel employee(Long id, UserRole role) {
        UserModel user = new UserModel();
        user.setId(id);
        user.setBranchId(BRANCH_ID);
        user.setRole(role);
        user.setUserName("emp" + id);
        return user;
    }

    private BranchModel branch(Long id) {
        BranchModel branch = new BranchModel();
        branch.setId(id);
        branch.setOperatingHours("08:00 - 22:00");
        return branch;
    }

    private ShiftAssignmentModel assignment(ShiftModel shift, UserModel staff) {
        ShiftAssignmentModel assignment = new ShiftAssignmentModel();
        assignment.setShift(shift);
        assignment.setStaff(staff);
        return assignment;
    }
}
