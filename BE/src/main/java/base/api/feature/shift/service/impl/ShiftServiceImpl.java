package base.api.feature.shift.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.shift.dto.request.AssignEmployeesRequest;
import base.api.feature.shift.dto.request.CreateShiftRequest;
import base.api.feature.shift.dto.request.ReplaceAssignedEmployeeRequest;
import base.api.feature.shift.dto.request.UpdateShiftRequest;
import base.api.feature.shift.dto.response.AvailableEmployeeResponse;
import base.api.feature.shift.dto.response.ScheduleDayResponse;
import base.api.feature.shift.dto.response.ShiftResponse;
import base.api.feature.shift.dto.response.WeeklyScheduleResponse;
import base.api.feature.shift.mapper.ShiftMapper;
import base.api.feature.shift.repository.ShiftAssignmentRepository;
import base.api.feature.shift.repository.ShiftRepository;
import base.api.feature.shift.service.IShiftService;
import base.api.shared.entity.ShiftAssignmentModel;
import base.api.shared.entity.ShiftModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.ShiftStatus;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BusinessException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ShiftServiceImpl implements IShiftService {

    private static final long MAX_SHIFTS_PER_DAY = 2L;
    private static final long MAX_WORKING_MINUTES_PER_DAY = 8L * 60L;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private ShiftAssignmentRepository assignmentRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IBranchRepository branchRepository;

    @Autowired
    private ShiftMapper shiftMapper;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public ShiftResponse create(CreateShiftRequest request) {
        UserModel currentUser = requireBranchManager();
        assertOwnBranch(request.getBranchId(), currentUser);
        validateBranchExists(request.getBranchId());
        validateShiftTime(request.getStartTime(), request.getEndTime());
        validateNonNegativeMoney(request.getOpeningCash(), "Opening cash must be greater than or equal to 0.");
        validateNonNegativeMoney(request.getExpectedCash(), "Expected cash must be greater than or equal to 0.");

        ShiftModel shift = new ShiftModel();
        shift.setBranchId(request.getBranchId());
        shift.setCreatedBy(currentUser.getId());
        shift.setStartTime(request.getStartTime());
        shift.setEndTime(request.getEndTime());
        shift.setOpeningCash(request.getOpeningCash());
        shift.setExpectedCash(request.getExpectedCash());
        shift.setStatus(ShiftStatus.DRAFT);

        return toResponse(shiftRepository.save(shift));
    }

    @Override
    @Transactional
    public ShiftResponse update(Long id, UpdateShiftRequest request) {
        ShiftModel shift = findShiftOrThrow(id);
        assertCanManageShift(shift);
        assertDraft(shift);
        validateShiftTime(request.getStartTime(), request.getEndTime());
        validateNonNegativeMoney(request.getOpeningCash(), "Opening cash must be greater than or equal to 0.");
        validateNonNegativeMoney(request.getExpectedCash(), "Expected cash must be greater than or equal to 0.");
        validateNonNegativeMoney(request.getActualCash(), "Actual cash must be greater than or equal to 0.");

        List<ShiftAssignmentModel> assignments = assignmentRepository.findByShiftId(shift.getId());
        for (ShiftAssignmentModel assignment : assignments) {
            validateEmployeeAvailability(
                    assignment.getStaff(),
                    request.getStartTime(),
                    request.getEndTime(),
                    assignment.getStaff().getRole(),
                    shift.getId());
        }

        shift.setStartTime(request.getStartTime());
        shift.setEndTime(request.getEndTime());
        shift.setOpeningCash(request.getOpeningCash());
        shift.setExpectedCash(request.getExpectedCash());
        shift.setActualCash(request.getActualCash());
        shift.setDifference(calculateDifference(request.getExpectedCash(), request.getActualCash()));

        return toResponse(shiftRepository.save(shift));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ShiftModel shift = findShiftOrThrow(id);
        assertCanManageShift(shift);
        assertDraft(shift);
        assignmentRepository.deleteAll(assignmentRepository.findByShiftId(shift.getId()));
        shiftRepository.delete(shift);
    }

    @Override
    public ShiftResponse getById(Long id) {
        ShiftModel shift = findShiftOrThrow(id);
        assertCanManageShift(shift);
        return toResponse(shift);
    }

    @Override
    public List<ShiftResponse> getAll(Long branchId) {
        UserModel currentUser = requireBranchManager();
        Long targetBranchId = branchId == null ? currentUser.getBranchId() : branchId;
        assertOwnBranch(targetBranchId, currentUser);

        return shiftRepository.findByBranchIdOrderByStartTimeAsc(targetBranchId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ShiftResponse assignEmployees(Long shiftId, AssignEmployeesRequest request) {
        ShiftModel shift = findShiftOrThrow(shiftId);
        assertCanManageShift(shift);
        assertDraft(shift);

        for (Long employeeId : request.getEmployeeIds()) {
            UserModel employee = findEmployeeOrThrow(employeeId);
            validateEmployeeForShift(employee, shift, request.getRequiredRole(), null);

            if (assignmentRepository.existsByShiftIdAndStaffId(shift.getId(), employee.getId())) {
                throw new BusinessException("Employee is already assigned to this shift.");
            }

            ShiftAssignmentModel assignment = new ShiftAssignmentModel();
            assignment.setShift(shift);
            assignment.setStaff(employee);
            assignmentRepository.save(assignment);
        }

        return toResponse(shift);
    }

    @Override
    @Transactional
    public void removeEmployee(Long shiftId, Long employeeId) {
        ShiftModel shift = findShiftOrThrow(shiftId);
        assertCanManageShift(shift);
        assertDraft(shift);

        ShiftAssignmentModel assignment = assignmentRepository.findByShiftIdAndStaffId(shiftId, employeeId)
                .orElseThrow(() -> new NotFoundException("Shift assignment not found."));
        assignmentRepository.delete(assignment);
    }

    @Override
    @Transactional
    public ShiftResponse replaceEmployee(
            Long shiftId,
            Long employeeId,
            ReplaceAssignedEmployeeRequest request) {

        ShiftModel shift = findShiftOrThrow(shiftId);
        assertCanManageShift(shift);
        assertDraft(shift);

        ShiftAssignmentModel assignment = assignmentRepository.findByShiftIdAndStaffId(shiftId, employeeId)
                .orElseThrow(() -> new NotFoundException("Shift assignment not found."));
        UserModel replacement = findEmployeeOrThrow(request.getReplacementEmployeeId());
        validateEmployeeForShift(replacement, shift, request.getRequiredRole(), shiftId);

        if (!employeeId.equals(replacement.getId())
                && assignmentRepository.existsByShiftIdAndStaffId(shiftId, replacement.getId())) {
            throw new BusinessException("Replacement employee is already assigned to this shift.");
        }

        assignment.setStaff(replacement);
        assignmentRepository.save(assignment);

        return toResponse(shift);
    }

    @Override
    @Transactional
    public ShiftResponse publish(Long id) {
        ShiftModel shift = findShiftOrThrow(id);
        UserModel currentUser = requireBranchManager();
        assertOwnBranch(shift.getBranchId(), currentUser);
        assertDraft(shift);

        shift.setStatus(ShiftStatus.PUBLISHED);
        shift.setApprovedBy(currentUser.getId());
        return toResponse(shiftRepository.save(shift));
    }

    @Override
    public WeeklyScheduleResponse getWeeklySchedule(Long branchId, LocalDate weekStart) {
        UserModel currentUser = requireBranchManager();
        assertOwnBranch(branchId, currentUser);
        validateBranchExists(branchId);

        LocalDate normalizedWeekStart = requireDate(weekStart, "Week start is required.");
        LocalDateTime start = normalizedWeekStart.atStartOfDay();
        LocalDateTime end = normalizedWeekStart.plusDays(7).atStartOfDay();

        List<ShiftModel> shifts = shiftRepository
                .findByBranchIdAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAsc(branchId, end, start);
        Map<Long, List<ShiftAssignmentModel>> assignmentsByShiftId = loadAssignmentsByShiftId(shifts);

        WeeklyScheduleResponse response = new WeeklyScheduleResponse();
        response.setBranchId(branchId);
        response.setWeekStart(normalizedWeekStart);
        response.setDays(buildScheduleDays(normalizedWeekStart, shifts, assignmentsByShiftId));
        return response;
    }

    @Override
    public List<AvailableEmployeeResponse> findAvailableEmployees(
            Long branchId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            UserRole requiredRole) {

        UserModel currentUser = requireBranchManager();
        assertOwnBranch(branchId, currentUser);
        validateBranchExists(branchId);

        LocalDate targetDate = requireDate(date, "Date is required.");
        LocalTime targetStart = requireTime(startTime, "Start time is required.");
        LocalTime targetEnd = requireTime(endTime, "End time is required.");
        UserRole targetRole = requireRole(requiredRole);
        LocalDateTime shiftStart = LocalDateTime.of(targetDate, targetStart);
        LocalDateTime shiftEnd = LocalDateTime.of(targetDate, targetEnd);
        validateShiftTime(shiftStart, shiftEnd);

        return userRepository.findByBranchIdAndRoleName(branchId, targetRole.name()).stream()
                .filter(employee -> isEmployeeAvailable(employee, shiftStart, shiftEnd, targetRole, null))
                .map(shiftMapper::toAvailableEmployeeResponse)
                .toList();
    }

    private ShiftResponse toResponse(ShiftModel shift) {
        return shiftMapper.toResponse(shift, assignmentRepository.findByShiftId(shift.getId()));
    }

    private List<ScheduleDayResponse> buildScheduleDays(
            LocalDate weekStart,
            List<ShiftModel> shifts,
            Map<Long, List<ShiftAssignmentModel>> assignmentsByShiftId) {

        Map<LocalDate, List<ShiftResponse>> shiftsByDate = shifts.stream()
                .collect(Collectors.groupingBy(
                        shift -> shift.getStartTime().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.mapping(
                                shift -> shiftMapper.toResponse(
                                        shift,
                                        assignmentsByShiftId.getOrDefault(shift.getId(), List.of())),
                                Collectors.toList())));

        List<ScheduleDayResponse> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            ScheduleDayResponse day = new ScheduleDayResponse();
            day.setDate(date);
            day.setShifts(shiftsByDate.getOrDefault(date, List.of()).stream()
                    .sorted(Comparator.comparing(ShiftResponse::getStartTime))
                    .toList());
            days.add(day);
        }
        return days;
    }

    private Map<Long, List<ShiftAssignmentModel>> loadAssignmentsByShiftId(List<ShiftModel> shifts) {
        if (shifts.isEmpty()) {
            return Map.of();
        }

        List<Long> shiftIds = shifts.stream().map(ShiftModel::getId).toList();
        return assignmentRepository.findByShiftIdIn(shiftIds).stream()
                .collect(Collectors.groupingBy(assignment -> assignment.getShift().getId()));
    }

    private void validateEmployeeForShift(
            UserModel employee,
            ShiftModel shift,
            UserRole requiredRole,
            Long excludedShiftId) {

        if (!Objects.equals(employee.getBranchId(), shift.getBranchId())) {
            throw new BusinessException("Employee must belong to the same branch as the shift.");
        }

        validateEmployeeRole(employee, requiredRole);
        validateEmployeeAvailability(employee, shift.getStartTime(), shift.getEndTime(), requiredRole, excludedShiftId);
    }

    private void validateEmployeeRole(UserModel employee, UserRole requiredRole) {
        UserRole targetRole = requireRole(requiredRole);
        if (employee.getRole() == null || employee.getRole().toWebRole() != targetRole.toWebRole()) {
            throw new BusinessException("Employee does not match the required role for this shift.");
        }
    }

    private void validateEmployeeAvailability(
            UserModel employee,
            LocalDateTime startTime,
            LocalDateTime endTime,
            UserRole requiredRole,
            Long excludedShiftId) {

        validateEmployeeRole(employee, requiredRole);

        boolean hasOverlap = assignmentRepository
                .findOverlappingAssignments(employee.getId(), startTime, endTime)
                .stream()
                .anyMatch(assignment -> !Objects.equals(assignment.getShift().getId(), excludedShiftId));
        if (hasOverlap) {
            throw new BusinessException("Employee already has an overlapping shift.");
        }

        LocalDateTime dayStart = startTime.toLocalDate().atStartOfDay();
        LocalDateTime nextDayStart = dayStart.plusDays(1);
        List<ShiftAssignmentModel> sameDayAssignments = assignmentRepository
                .findStaffAssignmentsOnDay(employee.getId(), dayStart, nextDayStart)
                .stream()
                .filter(assignment -> !Objects.equals(assignment.getShift().getId(), excludedShiftId))
                .toList();

        if (sameDayAssignments.size() + 1 > MAX_SHIFTS_PER_DAY) {
            throw new BusinessException("Employee cannot work more than 2 shifts per day.");
        }

        long assignedMinutes = sameDayAssignments.stream()
                .map(ShiftAssignmentModel::getShift)
                .mapToLong(shift -> Duration.between(shift.getStartTime(), shift.getEndTime()).toMinutes())
                .sum();
        long requestedMinutes = Duration.between(startTime, endTime).toMinutes();

        if (assignedMinutes + requestedMinutes > MAX_WORKING_MINUTES_PER_DAY) {
            throw new BusinessException("Employee cannot work more than 8 hours per day.");
        }
    }

    private boolean isEmployeeAvailable(
            UserModel employee,
            LocalDateTime startTime,
            LocalDateTime endTime,
            UserRole requiredRole,
            Long excludedShiftId) {

        try {
            validateEmployeeAvailability(employee, startTime, endTime, requiredRole, excludedShiftId);
            return true;
        } catch (BusinessException ex) {
            return false;
        }
    }

    private void validateShiftTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new BusinessException("Start time and end time are required.");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException("End time must be after start time.");
        }
    }

    private void validateNonNegativeMoney(BigDecimal value, String message) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(message);
        }
    }

    private BigDecimal calculateDifference(BigDecimal expectedCash, BigDecimal actualCash) {
        if (expectedCash == null || actualCash == null) {
            return null;
        }
        return actualCash.subtract(expectedCash);
    }

    private UserModel requireBranchManager() {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole role = currentUserProvider.getCurrentUserRole();
        if (role != UserRole.BRANCH_MANAGER) {
            throw new BusinessException("Only branch managers can manage shifts.");
        }
        if (currentUser.getBranchId() == null) {
            throw new BusinessException("Branch manager is not assigned to a branch.");
        }
        return currentUser;
    }

    private void assertCanManageShift(ShiftModel shift) {
        UserModel currentUser = requireBranchManager();
        assertOwnBranch(shift.getBranchId(), currentUser);
    }

    private void assertOwnBranch(Long branchId, UserModel currentUser) {
        if (branchId == null) {
            throw new BusinessException("Branch is required.");
        }
        if (!Objects.equals(currentUser.getBranchId(), branchId)) {
            throw new BusinessException("Branch managers can only manage shifts in their own branch.");
        }
    }

    private void assertDraft(ShiftModel shift) {
        if (shift.getStatus() != ShiftStatus.DRAFT) {
            throw new BusinessException("Shift cannot be modified once it is no longer DRAFT.");
        }
    }

    private void validateBranchExists(Long branchId) {
        if (!branchRepository.existsById(branchId)) {
            throw new NotFoundException("Branch not found.");
        }
    }

    private ShiftModel findShiftOrThrow(Long id) {
        return shiftRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Shift not found."));
    }

    private UserModel findEmployeeOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Employee not found."));
    }

    private LocalDate requireDate(LocalDate date, String message) {
        if (date == null) {
            throw new BusinessException(message);
        }
        return date;
    }

    private LocalTime requireTime(LocalTime time, String message) {
        if (time == null) {
            throw new BusinessException(message);
        }
        return time;
    }

    private UserRole requireRole(UserRole role) {
        if (role == null) {
            throw new BusinessException("Required role is required.");
        }
        return role;
    }
}
