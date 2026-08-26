package base.api.feature.shift.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.shift.dto.request.AssignEmployeesRequest;
import base.api.feature.shift.dto.request.AssignSlotRequest;
import base.api.feature.shift.dto.request.CreateShiftRequest;
import base.api.feature.shift.dto.request.ReplaceAssignedEmployeeRequest;
import base.api.feature.shift.dto.request.SetupAndPublishWeekRequest;
import base.api.feature.shift.dto.request.SetupWeekSlotRequest;
import base.api.feature.shift.dto.request.UpdateOpeningCashRequest;
import base.api.feature.shift.dto.request.UpdateShiftRequest;
import base.api.feature.shift.dto.request.WeekScheduleRequest;
import base.api.feature.shift.dto.response.AssignedEmployeeResponse;
import base.api.feature.shift.dto.response.AvailableEmployeeResponse;
import base.api.feature.shift.dto.response.CopyWeekResponse;
import base.api.feature.shift.dto.response.PublishWeekResponse;
import base.api.feature.shift.dto.response.ScheduleDayResponse;
import base.api.feature.shift.dto.response.SetupAndPublishWeekResponse;
import base.api.feature.shift.dto.response.ShiftResponse;
import base.api.feature.shift.dto.response.WeekSetupResponse;
import base.api.feature.shift.dto.response.WeekSetupSlotResponse;
import base.api.feature.shift.dto.response.WeeklyScheduleResponse;
import base.api.feature.shift.mapper.ShiftMapper;
import base.api.feature.shift.repository.ShiftAssignmentRepository;
import base.api.feature.shift.repository.ShiftRepository;
import base.api.feature.shift.service.IShiftService;
import base.api.feature.shift.util.ShiftSlotDeriver;
import base.api.shared.entity.BranchModel;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ShiftServiceImpl implements IShiftService {

    private static final int MAX_SHIFT_HOURS = 6;
    private static final int MAX_EMPLOYEES_PER_SHIFT = 3;

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

    /** Cash float handed to the first shift of each day when the caller does not supply one. */
    @Value("${shift.default-opening-cash:2000000}")
    private BigDecimal defaultOpeningCash;

    @Override
    @Transactional
    public ShiftResponse create(CreateShiftRequest request) {
        UserModel currentUser = requireBranchManager();
        assertOwnBranch(request.getBranchId(), currentUser);
        validateBranchExists(request.getBranchId());
        validateShiftTime(request.getStartTime(), request.getEndTime());
        validateNoOverlappingShift(request.getBranchId(), request.getStartTime(), request.getEndTime(), null);
        validateNonNegativeMoney(request.getOpeningCash(), "Opening cash must be greater than or equal to 0.");
        validateNonNegativeMoney(request.getExpectedCash(), "Expected cash must be greater than or equal to 0.");

        BigDecimal openingCash = request.getOpeningCash();
        if (openingCash == null
                && isEarliestShiftOfDay(request.getBranchId(), request.getStartTime())) {
            openingCash = defaultOpeningCash;
        }

        ShiftModel shift = new ShiftModel();
        shift.setBranchId(request.getBranchId());
        shift.setCreatedBy(currentUser.getId());
        shift.setStartTime(request.getStartTime());
        shift.setEndTime(request.getEndTime());
        shift.setOpeningCash(openingCash);
        shift.setExpectedCash(request.getExpectedCash());
        shift.setStatus(ShiftStatus.DRAFT);

        shiftRepository.save(shift);
        return shiftMapper.toResponse(shift, List.of());
    }

    @Override
    @Transactional
    public ShiftResponse update(Long id, UpdateShiftRequest request) {
        ShiftModel shift = findShiftOrThrow(id);
        assertCanManageShift(shift);
        assertDraft(shift);
        validateShiftTime(request.getStartTime(), request.getEndTime());
        validateNoOverlappingShift(shift.getBranchId(), request.getStartTime(), request.getEndTime(), shift.getId());
        validateNonNegativeMoney(request.getOpeningCash(), "Opening cash must be greater than or equal to 0.");
        validateNonNegativeMoney(request.getExpectedCash(), "Expected cash must be greater than or equal to 0.");
        validateNonNegativeMoney(request.getActualCash(), "Actual cash must be greater than or equal to 0.");

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
    public ShiftResponse updateOpeningCash(Long id, UpdateOpeningCashRequest request) {
        ShiftModel shift = findShiftOrThrow(id);
        assertCanManageShift(shift);
        assertOpeningCashEditable(shift);
        validateNonNegativeMoney(
                request.getOpeningCash(),
                "Opening cash must be greater than or equal to 0.");

        shift.setOpeningCash(request.getOpeningCash());
        return toResponse(shiftRepository.save(shift));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ShiftModel shift = findShiftOrThrow(id);
        assertCanManageShift(shift);
        assertDraft(shift);
        assignmentRepository.deleteAllByShiftId(shift.getId());
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

        List<ShiftModel> shifts = shiftRepository.findByBranchIdOrderByStartTimeAsc(targetBranchId);
        Map<Long, List<ShiftAssignmentModel>> assignmentsByShiftId = loadAssignmentsByShiftId(shifts);
        return shifts.stream()
                .map(shift -> shiftMapper.toResponse(
                        shift,
                        assignmentsByShiftId.getOrDefault(shift.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public ShiftResponse assignEmployees(Long shiftId, AssignEmployeesRequest request) {
        ShiftModel shift = findShiftOrThrow(shiftId);
        assertCanManageShift(shift);
        assertDraft(shift);

        List<Long> employeeIds = normalizeIds(request.getEmployeeIds());
        List<ShiftAssignmentModel> currentAssignments =
                new ArrayList<>(assignmentRepository.findByShiftId(shift.getId()));
        Set<Long> currentEmployeeIds = currentAssignments.stream()
                .map(assignment -> assignment.getStaff().getId())
                .collect(Collectors.toSet());
        if (employeeIds.stream().anyMatch(currentEmployeeIds::contains)) {
            throw new BusinessException("Employee is already assigned to this shift.");
        }
        assertEmployeeCap(currentAssignments.size() + employeeIds.size());

        Map<Long, UserModel> employeesById = userRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(UserModel::getId, employee -> employee));
        if (employeesById.size() != employeeIds.size()) {
            throw new NotFoundException("One or more selected employees were not found.");
        }
        List<ShiftAssignmentModel> newAssignments = new ArrayList<>();
        for (Long employeeId : employeeIds) {
            UserModel employee = employeesById.get(employeeId);
            validateEmployeeForShift(employee, shift, request.getRequiredRole());

            ShiftAssignmentModel assignment = new ShiftAssignmentModel();
            assignment.setShift(shift);
            assignment.setStaff(employee);
            assignment.setAssignedRole(requireRole(request.getRequiredRole()).toWebRole());
            newAssignments.add(assignment);
        }
        assignmentRepository.saveAll(newAssignments);
        currentAssignments.addAll(newAssignments);

        return shiftMapper.toResponse(shift, currentAssignments);
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
        validateEmployeeForShift(replacement, shift, request.getRequiredRole());

        if (!employeeId.equals(replacement.getId())
                && assignmentRepository.existsByShiftIdAndStaffId(shiftId, replacement.getId())) {
            throw new BusinessException("Replacement employee is already assigned to this shift.");
        }

        assignment.setStaff(replacement);
        assignment.setAssignedRole(requireRole(request.getRequiredRole()).toWebRole());
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
        List<ShiftAssignmentModel> assignments = assignmentRepository.findByShiftId(shift.getId());
        LocalDate day = shift.getStartTime().toLocalDate();
        List<ShiftModel> dayShifts = shiftRepository
                .findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        shift.getBranchId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        validatePublishStaffing(shift, assignments, dayShifts);

        shift.setStatus(ShiftStatus.PUBLISHED);
        shift.setApprovedBy(currentUser.getId());
        shiftRepository.save(shift);
        return shiftMapper.toResponse(shift, assignments);
    }

    @Override
    @Transactional
    public ShiftResponse assignToSlot(AssignSlotRequest request) {
        UserModel currentUser = requireBranchManager();
        assertOwnBranch(request.getBranchId(), currentUser);
        BranchModel branch = findBranchOrThrow(request.getBranchId());
        validateShiftTime(request.getStartTime(), request.getEndTime());
        validateNonNegativeMoney(request.getOpeningCash(), "Opening cash must be greater than or equal to 0.");

        boolean firstOfDay = isFirstTemplateSlot(branch, request.getStartTime(), request.getEndTime());
        List<Long> cashiers = normalizeIds(request.getCashiers());
        List<Long> inventoryStaff = normalizeIds(request.getInventoryStaff());
        ensureNoRoleOverlap(cashiers, inventoryStaff);
        if (!(cashiers.isEmpty() && inventoryStaff.isEmpty())) {
            assertEmployeeCap(cashiers.size() + inventoryStaff.size());
        }

        Optional<ShiftModel> exactMatch = shiftRepository.findByBranchIdAndStartTimeAndEndTime(
                request.getBranchId(), request.getStartTime(), request.getEndTime());

        if (exactMatch.isPresent()) {
            ShiftModel shift = exactMatch.get();
            assertOwnBranch(shift.getBranchId(), currentUser);
            if (shift.getStatus() != ShiftStatus.DRAFT) {
                throw new BusinessException("This slot is already published and cannot be modified.");
            }
            if (cashiers.isEmpty() && inventoryStaff.isEmpty()) {
                assignmentRepository.deleteAllByShiftId(shift.getId());
                shiftRepository.delete(shift);
                return null;
            }
            // Only overwrite when the caller actually sent a figure — a plain re-save of the
            // staffing grid must not reset a float the BM already adjusted.
            if (firstOfDay && request.getOpeningCash() != null) {
                shift.setOpeningCash(request.getOpeningCash());
                shift = shiftRepository.save(shift);
            }
            List<ShiftAssignmentModel> assignments =
                    syncSlotAssignments(shift, cashiers, inventoryStaff);
            return shiftMapper.toResponse(shift, assignments);
        }

        if (cashiers.isEmpty() && inventoryStaff.isEmpty()) {
            return null;
        }

        validateNoOverlappingShift(request.getBranchId(), request.getStartTime(), request.getEndTime(), null);

        ShiftModel shift = new ShiftModel();
        shift.setBranchId(request.getBranchId());
        shift.setCreatedBy(currentUser.getId());
        shift.setStartTime(request.getStartTime());
        shift.setEndTime(request.getEndTime());
        shift.setOpeningCash(resolveOpeningCash(request.getOpeningCash(), firstOfDay));
        shift.setExpectedCash(BigDecimal.ZERO);
        shift.setStatus(ShiftStatus.DRAFT);
        shift = shiftRepository.save(shift);

        List<ShiftAssignmentModel> assignments =
                syncSlotAssignments(shift, cashiers, inventoryStaff);
        return shiftMapper.toResponse(shift, assignments);
    }

    @Override
    @Transactional
    public PublishWeekResponse publishWeek(WeekScheduleRequest request) {
        UserModel currentUser = requireBranchManager();
        assertOwnBranch(request.getBranchId(), currentUser);
        validateBranchExists(request.getBranchId());

        LocalDate weekStart = mondayOf(requireDate(request.getWeekStart(), "Week start is required."));
        LocalDateTime rangeStart = weekStart.atStartOfDay();
        LocalDateTime rangeEnd = weekStart.plusDays(7).atStartOfDay();

        List<ShiftModel> weekShifts = shiftRepository
                .findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        request.getBranchId(), rangeStart, rangeEnd);
        Map<Long, List<ShiftAssignmentModel>> assignmentsByShiftId = loadAssignmentsByShiftId(weekShifts);
        Map<LocalDate, List<ShiftModel>> shiftsByDay = weekShifts.stream()
                .collect(Collectors.groupingBy(shift -> shift.getStartTime().toLocalDate()));
        List<ShiftModel> drafts = weekShifts.stream()
                .filter(shift -> shift.getStatus() == ShiftStatus.DRAFT)
                .toList();

        PublishWeekResponse response = new PublishWeekResponse();
        List<ShiftModel> publishable = new ArrayList<>();
        for (ShiftModel shift : drafts) {
            String issue = publishStaffingIssue(
                    shift,
                    assignmentsByShiftId.getOrDefault(shift.getId(), List.of()),
                    shiftsByDay.getOrDefault(shift.getStartTime().toLocalDate(), List.of()));
            if (issue != null) {
                response.getSkipped().add(new PublishWeekResponse.SkippedShift(shift.getId(), issue));
                continue;
            }
            shift.setStatus(ShiftStatus.PUBLISHED);
            shift.setApprovedBy(currentUser.getId());
            publishable.add(shift);
        }
        shiftRepository.saveAll(publishable);
        response.setPublished(publishable.size());
        response.setSchedule(buildWeeklySchedule(
                request.getBranchId(), weekStart, weekShifts, assignmentsByShiftId));
        return response;
    }

    @Override
    @Transactional
    public CopyWeekResponse copyPreviousWeek(WeekScheduleRequest request) {
        UserModel currentUser = requireBranchManager();
        assertOwnBranch(request.getBranchId(), currentUser);
        BranchModel branch = findBranchOrThrow(request.getBranchId());

        LocalDate targetWeekStart = mondayOf(requireDate(request.getWeekStart(), "Week start is required."));
        LocalDate sourceWeekStart = targetWeekStart.minusDays(7);
        LocalDateTime sourceStart = sourceWeekStart.atStartOfDay();
        LocalDateTime sourceEnd = targetWeekStart.atStartOfDay();
        LocalDateTime targetEnd = targetWeekStart.plusDays(7).atStartOfDay();

        List<ShiftModel> sourceShifts = shiftRepository
                .findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        request.getBranchId(), sourceStart, sourceEnd);
        List<ShiftModel> targetShifts = new ArrayList<>(shiftRepository
                .findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        request.getBranchId(), sourceEnd, targetEnd));
        Map<Long, List<ShiftAssignmentModel>> sourceAssignments = loadAssignmentsByShiftId(sourceShifts);
        Map<Long, List<ShiftAssignmentModel>> targetAssignments = new LinkedHashMap<>(
                loadAssignmentsByShiftId(targetShifts));
        Map<String, ShiftModel> targetBySlot = targetShifts.stream()
                .collect(Collectors.toMap(
                        shift -> slotKey(shift.getStartTime(), shift.getEndTime()),
                        shift -> shift,
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        Map<Long, UserModel> employeesById = sourceAssignments.values().stream()
                .flatMap(List::stream)
                .map(ShiftAssignmentModel::getStaff)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(UserModel::getId, employee -> employee, (first, ignored) -> first));
        List<ShiftAssignmentModel> assignmentsToDelete = new ArrayList<>();
        List<ShiftAssignmentModel> assignmentsToSave = new ArrayList<>();

        CopyWeekResponse response = new CopyWeekResponse();
        int copied = 0;
        int skipped = 0;

        for (ShiftModel source : sourceShifts) {
            List<ShiftAssignmentModel> assignments = sourceAssignments.getOrDefault(source.getId(), List.of());
            if (assignments.isEmpty()) {
                skipped += 1;
                continue;
            }
            if (assignments.size() > MAX_EMPLOYEES_PER_SHIFT) {
                skipped += 1;
                response.getConflicts().add(formatSlotConflict(
                        source.getStartTime(),
                        source.getEndTime(),
                        "Source slot exceeds " + MAX_EMPLOYEES_PER_SHIFT + " employees and was skipped."));
                continue;
            }

            long dayOffset = java.time.temporal.ChronoUnit.DAYS.between(sourceWeekStart, source.getStartTime().toLocalDate());
            if (dayOffset < 0 || dayOffset > 6) {
                skipped += 1;
                continue;
            }

            LocalDate targetDate = targetWeekStart.plusDays(dayOffset);
            LocalDateTime targetStart = LocalDateTime.of(targetDate, source.getStartTime().toLocalTime());
            LocalDateTime targetEndTime = LocalDateTime.of(targetDate, source.getEndTime().toLocalTime());
            String targetKey = slotKey(targetStart, targetEndTime);
            ShiftModel existing = targetBySlot.get(targetKey);
            List<Long> cashiers = assignments.stream()
                    .filter(a -> effectiveAssignmentRole(a) == UserRole.CASHIER)
                    .map(a -> a.getStaff().getId())
                    .toList();
            List<Long> inventoryStaff = assignments.stream()
                    .filter(a -> effectiveAssignmentRole(a) == UserRole.INVENTORY_STAFF)
                    .map(a -> a.getStaff().getId())
                    .toList();

            if (existing != null) {
                List<ShiftAssignmentModel> existingAssignments =
                        targetAssignments.getOrDefault(existing.getId(), List.of());
                if (!existingAssignments.isEmpty() || existing.getStatus() != ShiftStatus.DRAFT) {
                    skipped += 1;
                    continue;
                }
                try {
                    AssignmentChanges changes = planSlotAssignmentSync(
                            existing, cashiers, inventoryStaff, existingAssignments, employeesById);
                    assignmentsToDelete.addAll(changes.toDelete());
                    assignmentsToSave.addAll(changes.toSave());
                    targetAssignments.put(existing.getId(), changes.result());
                    copied += 1;
                } catch (BusinessException ex) {
                    skipped += 1;
                    response.getConflicts().add(formatSlotConflict(targetStart, targetEndTime, ex.getMessage()));
                }
                continue;
            }

            boolean overlaps = targetShifts.stream()
                    .anyMatch(shift -> shift.getStartTime().isBefore(targetEndTime)
                            && shift.getEndTime().isAfter(targetStart));
            if (overlaps) {
                skipped += 1;
                response.getConflicts().add(formatSlotConflict(
                        targetStart, targetEndTime, "Another shift already occupies this time range."));
                continue;
            }

            try {
                validateDesiredEmployees(
                        request.getBranchId(), cashiers, inventoryStaff, employeesById);
                ShiftModel draft = new ShiftModel();
                draft.setBranchId(request.getBranchId());
                draft.setCreatedBy(currentUser.getId());
                draft.setStartTime(targetStart);
                draft.setEndTime(targetEndTime);
                // Carry last week's float forward so the copied week does not silently reset to 0.
                draft.setOpeningCash(resolveOpeningCash(
                        source.getOpeningCash(),
                        isFirstTemplateSlot(branch, targetStart, targetEndTime)));
                draft.setExpectedCash(BigDecimal.ZERO);
                draft.setStatus(ShiftStatus.DRAFT);
                draft = shiftRepository.save(draft);

                AssignmentChanges changes = planSlotAssignmentSync(
                        draft, cashiers, inventoryStaff, List.of(), employeesById);
                assignmentsToSave.addAll(changes.toSave());
                targetAssignments.put(draft.getId(), changes.result());
                targetShifts.add(draft);
                targetBySlot.put(targetKey, draft);
                copied += 1;
            } catch (BusinessException ex) {
                skipped += 1;
                response.getConflicts().add(formatSlotConflict(targetStart, targetEndTime, ex.getMessage()));
            }
        }

        if (!assignmentsToDelete.isEmpty()) {
            assignmentRepository.deleteAllInBatch(assignmentsToDelete);
        }
        if (!assignmentsToSave.isEmpty()) {
            assignmentRepository.saveAll(assignmentsToSave);
        }
        response.setCopied(copied);
        response.setSkipped(skipped);
        response.setSchedule(buildWeeklySchedule(
                request.getBranchId(), targetWeekStart, targetShifts, targetAssignments));
        return response;
    }

    @Override
    public WeekSetupResponse getWeekSetup(Long branchId, LocalDate weekStart) {
        UserModel currentUser = requireBranchManager();
        assertOwnBranch(branchId, currentUser);
        BranchModel branch = findBranchOrThrow(branchId);

        LocalDate normalizedWeekStart = mondayOf(requireDate(weekStart, "Week start is required."));
        List<ShiftSlotDeriver.SlotTemplate> templates = ShiftSlotDeriver.derive(branch.getOperatingHours());
        LocalDateTime rangeStart = normalizedWeekStart.atStartOfDay();
        LocalDateTime rangeEnd = normalizedWeekStart.plusDays(7).atStartOfDay();
        List<ShiftModel> weekShifts = shiftRepository
                .findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        branchId, rangeStart, rangeEnd);
        Map<Long, List<ShiftAssignmentModel>> assignmentsByShiftId = loadAssignmentsByShiftId(weekShifts);
        Map<String, ShiftModel> shiftsBySlot = weekShifts.stream()
                .collect(Collectors.toMap(
                        shift -> slotKey(shift.getStartTime(), shift.getEndTime()),
                        shift -> shift,
                        (first, ignored) -> first));
        Map<LocalDate, List<ShiftModel>> shiftsByDay = weekShifts.stream()
                .collect(Collectors.groupingBy(shift -> shift.getStartTime().toLocalDate()));

        WeekSetupResponse response = new WeekSetupResponse();
        response.setBranchId(branchId);
        response.setWeekStart(normalizedWeekStart);
        response.setOperatingHours(branch.getOperatingHours());
        response.setMaxEmployeesPerShift(MAX_EMPLOYEES_PER_SHIFT);
        List<UserModel> candidates = userRepository.findByBranchIdAndRoleNames(
                branchId,
                List.of(UserRole.CASHIER.name(), UserRole.INVENTORY_STAFF.name()));
        response.setCashiers(candidates.stream()
                .filter(employee -> employee.getRole() == UserRole.CASHIER)
                .map(shiftMapper::toAvailableEmployeeResponse)
                .toList());
        response.setInventoryStaff(candidates.stream()
                .filter(employee -> employee.getRole() == UserRole.INVENTORY_STAFF)
                        .map(shiftMapper::toAvailableEmployeeResponse)
                        .toList());

        List<WeekSetupSlotResponse> slots = new ArrayList<>();
        for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
            LocalDate date = normalizedWeekStart.plusDays(dayOffset);
            for (ShiftSlotDeriver.SlotTemplate template : templates) {
                LocalDateTime start = LocalDateTime.of(date, template.start());
                LocalDateTime end = LocalDateTime.of(date, template.end());
                ShiftModel match = shiftsBySlot.get(slotKey(start, end));
                if (match == null) {
                    match = shiftsByDay.getOrDefault(date, List.of()).stream()
                                .filter(s -> s.getStartTime().toLocalDate().equals(date)
                                        && !s.getStartTime().toLocalTime().isBefore(template.start())
                                        && s.getStartTime().toLocalTime().isBefore(template.end()))
                                .min(Comparator.comparing(ShiftModel::getStartTime))
                                .orElse(null);
                }

                WeekSetupSlotResponse slot = new WeekSetupSlotResponse();
                slot.setDate(date);
                slot.setStartTime(start);
                slot.setEndTime(end);
                slot.setSlotIndex(template.index());
                slot.setFirst(template.first());
                slot.setLast(template.last());

                if (match != null) {
                    List<ShiftAssignmentModel> assignments =
                            assignmentsByShiftId.getOrDefault(match.getId(), List.of());
                    List<AssignedEmployeeResponse> assigned = assignments.stream()
                            .map(shiftMapper::toAssignedEmployeeResponse)
                            .toList();
                    slot.setShiftId(match.getId());
                    // Show what is stored; legacy rows with no float fall back to the suggestion.
                    slot.setOpeningCash(match.getOpeningCash() != null
                            ? match.getOpeningCash()
                            : resolveOpeningCash(null, template.first()));
                    slot.setStatus(match.getStatus() != null ? match.getStatus().name() : null);
                    slot.setPublished(match.getStatus() == ShiftStatus.PUBLISHED);
                    slot.setReadOnly(match.getStatus() == ShiftStatus.PUBLISHED);
                    slot.setAssignedEmployees(assigned);
                    slot.setCashiers(assigned.stream()
                            .filter(a -> a.getRole() == UserRole.CASHIER)
                            .map(AssignedEmployeeResponse::getEmployeeId)
                            .toList());
                    slot.setInventoryStaff(assigned.stream()
                            .filter(a -> a.getRole() == UserRole.INVENTORY_STAFF)
                            .map(AssignedEmployeeResponse::getEmployeeId)
                            .toList());
                } else {
                    slot.setPublished(false);
                    slot.setReadOnly(false);
                    slot.setOpeningCash(resolveOpeningCash(null, template.first()));
                }
                slots.add(slot);
            }
        }
        response.setSlots(slots);
        return response;
    }

    @Override
    @Transactional
    public SetupAndPublishWeekResponse setupAndPublishWeek(SetupAndPublishWeekRequest request) {
        UserModel currentUser = requireBranchManager();
        assertOwnBranch(request.getBranchId(), currentUser);
        BranchModel branch = findBranchOrThrow(request.getBranchId());

        LocalDate weekStart = mondayOf(requireDate(request.getWeekStart(), "Week start is required."));
        List<ShiftSlotDeriver.SlotTemplate> templates = ShiftSlotDeriver.derive(branch.getOperatingHours());
        if (templates.isEmpty()) {
            throw new BusinessException("No schedule slots for this branch's operating hours.");
        }

        LocalDateTime rangeStart = weekStart.atStartOfDay();
        LocalDateTime rangeEnd = weekStart.plusDays(7).atStartOfDay();
        List<ShiftModel> weekShifts = new ArrayList<>(shiftRepository
                .findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        request.getBranchId(), rangeStart, rangeEnd));

        Map<String, ShiftModel> existingByKey = new LinkedHashMap<>();
        for (ShiftModel shift : weekShifts) {
            existingByKey.put(slotKey(shift.getStartTime(), shift.getEndTime()), shift);
        }

        List<VirtualSlot> expectedEditable = new ArrayList<>();
        Map<String, VirtualSlot> expectedByKey = new LinkedHashMap<>();
        for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
            LocalDate date = weekStart.plusDays(dayOffset);
            for (ShiftSlotDeriver.SlotTemplate template : templates) {
                LocalDateTime start = LocalDateTime.of(date, template.start());
                LocalDateTime end = LocalDateTime.of(date, template.end());
                String key = slotKey(start, end);
                ShiftModel existing = existingByKey.get(key);
                boolean published = existing != null && existing.getStatus() == ShiftStatus.PUBLISHED;
                VirtualSlot virtual = new VirtualSlot(start, end, template.first(), template.last(), published, existing);
                if (!published) {
                    expectedEditable.add(virtual);
                    expectedByKey.put(key, virtual);
                }
            }
        }

        if (expectedEditable.isEmpty()) {
            throw new BusinessException("All slots this week are already published.");
        }

        Map<String, SetupWeekSlotRequest> payloadByKey = new LinkedHashMap<>();
        for (SetupWeekSlotRequest slotReq : request.getSlots()) {
            if (slotReq.getStartTime() == null || slotReq.getEndTime() == null) {
                throw new BusinessException("Each slot needs start and end times.");
            }
            validateShiftTime(slotReq.getStartTime(), slotReq.getEndTime());
            String key = slotKey(slotReq.getStartTime(), slotReq.getEndTime());
            ShiftModel existing = existingByKey.get(key);
            if (existing != null && existing.getStatus() == ShiftStatus.PUBLISHED) {
                throw new BusinessException("Cannot modify published slot " + key + ".");
            }
            if (!expectedByKey.containsKey(key)) {
                throw new BusinessException("Unexpected slot in payload: " + key + ".");
            }
            if (payloadByKey.containsKey(key)) {
                throw new BusinessException("Duplicate slot in payload: " + key + ".");
            }
            payloadByKey.put(key, slotReq);
        }

        if (payloadByKey.isEmpty()) {
            throw new BusinessException("Select at least one ready slot to publish.");
        }

        Set<Long> requestedEmployeeIds = payloadByKey.values().stream()
                .flatMap(slot -> {
                    List<Long> ids = new ArrayList<>(normalizeIds(slot.getCashiers()));
                    ids.addAll(normalizeIds(slot.getInventoryStaff()));
                    return ids.stream();
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, UserModel> employeesById = userRepository.findAllById(requestedEmployeeIds).stream()
                .collect(Collectors.toMap(UserModel::getId, employee -> employee));
        if (employeesById.size() != requestedEmployeeIds.size()) {
            throw new NotFoundException("One or more selected employees were not found.");
        }

        // Partial week allowed (holidays / days off): only validate and publish slots in the payload.
        List<VirtualSlot> slotsToPublish = new ArrayList<>();
        for (Map.Entry<String, SetupWeekSlotRequest> entry : payloadByKey.entrySet()) {
            VirtualSlot virtual = expectedByKey.get(entry.getKey());
            SetupWeekSlotRequest slotReq = entry.getValue();
            List<Long> cashiers = normalizeIds(slotReq.getCashiers());
            List<Long> inventoryStaff = normalizeIds(slotReq.getInventoryStaff());
            ensureNoRoleOverlap(cashiers, inventoryStaff);
            validateNonNegativeMoney(
                    slotReq.getOpeningCash(),
                    "Opening cash must be greater than or equal to 0.");
            int total = cashiers.size() + inventoryStaff.size();
            if (total < 1) {
                throw new BusinessException(
                        "Slot " + entry.getKey() + " needs at least one employee.");
            }
            assertEmployeeCap(total);
            if (cashiers.isEmpty()) {
                throw new BusinessException(
                        "Slot " + entry.getKey() + " needs at least one Cashier.");
            }
            if ((virtual.first() || virtual.last()) && inventoryStaff.isEmpty()) {
                throw new BusinessException(
                        "Opening/closing slot " + entry.getKey()
                                + " needs at least one Inventory Staff.");
            }

            for (Long id : cashiers) {
                UserModel employee = employeesById.get(id);
                if (!Objects.equals(employee.getBranchId(), request.getBranchId())) {
                    throw new BusinessException("Employee must belong to the same branch as the shift.");
                }
                validateEmployeeRole(employee, UserRole.CASHIER);
            }
            for (Long id : inventoryStaff) {
                UserModel employee = employeesById.get(id);
                if (!Objects.equals(employee.getBranchId(), request.getBranchId())) {
                    throw new BusinessException("Employee must belong to the same branch as the shift.");
                }
                validateEmployeeRole(employee, UserRole.INVENTORY_STAFF);
            }
            slotsToPublish.add(virtual);
        }

        int published = 0;
        Map<Long, List<ShiftAssignmentModel>> assignmentsByShiftId = new LinkedHashMap<>(
                loadAssignmentsByShiftId(weekShifts));
        List<ShiftAssignmentModel> assignmentsToDelete = new ArrayList<>();
        List<ShiftAssignmentModel> assignmentsToSave = new ArrayList<>();
        List<ShiftModel> shiftsToSave = new ArrayList<>();
        for (VirtualSlot virtual : slotsToPublish) {
            SetupWeekSlotRequest slotReq = payloadByKey.get(slotKey(virtual.start(), virtual.end()));
            List<Long> cashiers = normalizeIds(slotReq.getCashiers());
            List<Long> inventoryStaff = normalizeIds(slotReq.getInventoryStaff());

            ShiftModel shift = virtual.existing();
            boolean isNewShift = shift == null;
            if (shift == null) {
                boolean overlaps = weekShifts.stream()
                        .anyMatch(existing -> existing.getStartTime().isBefore(virtual.end())
                                && existing.getEndTime().isAfter(virtual.start()));
                if (overlaps) {
                    throw new BusinessException("Shift already exists in this time range.");
                }
                shift = new ShiftModel();
                shift.setBranchId(request.getBranchId());
                shift.setCreatedBy(currentUser.getId());
                shift.setStartTime(virtual.start());
                shift.setEndTime(virtual.end());
                shift.setExpectedCash(BigDecimal.ZERO);
                shift.setStatus(ShiftStatus.DRAFT);
                shift = shiftRepository.save(shift);
                weekShifts.add(shift);
            } else if (shift.getStatus() != ShiftStatus.DRAFT) {
                throw new BusinessException("Slot " + slotKey(virtual.start(), virtual.end()) + " is not editable.");
            }
            // A new slot gets the resolved float. An existing draft only changes when the caller
            // sent a figure, so re-publishing never wipes a handover already recorded on it.
            if (isNewShift) {
                shift.setOpeningCash(resolveOpeningCash(slotReq.getOpeningCash(), virtual.first()));
            } else if (virtual.first() && slotReq.getOpeningCash() != null) {
                shift.setOpeningCash(slotReq.getOpeningCash());
            }

            AssignmentChanges changes = planSlotAssignmentSync(
                    shift,
                    cashiers,
                    inventoryStaff,
                    assignmentsByShiftId.getOrDefault(shift.getId(), List.of()),
                    employeesById);
            assignmentsToDelete.addAll(changes.toDelete());
            assignmentsToSave.addAll(changes.toSave());
            assignmentsByShiftId.put(shift.getId(), changes.result());
            shift.setStatus(ShiftStatus.PUBLISHED);
            shift.setApprovedBy(currentUser.getId());
            shiftsToSave.add(shift);
            published += 1;
        }

        if (!assignmentsToDelete.isEmpty()) {
            assignmentRepository.deleteAllInBatch(assignmentsToDelete);
        }
        if (!assignmentsToSave.isEmpty()) {
            assignmentRepository.saveAll(assignmentsToSave);
        }
        shiftRepository.saveAll(shiftsToSave);
        SetupAndPublishWeekResponse response = new SetupAndPublishWeekResponse();
        response.setPublished(published);
        response.setSchedule(buildWeeklySchedule(
                request.getBranchId(), weekStart, weekShifts, assignmentsByShiftId));
        return response;
    }

    @Override
    public WeeklyScheduleResponse getWeeklySchedule(Long branchId, LocalDate weekStart) {
        UserModel currentUser = requireBranchManager();
        assertOwnBranch(branchId, currentUser);
        validateBranchExists(branchId);

        LocalDate normalizedWeekStart = mondayOf(requireDate(weekStart, "Week start is required."));
        LocalDateTime start = normalizedWeekStart.atStartOfDay();
        LocalDateTime end = normalizedWeekStart.plusDays(7).atStartOfDay();

        List<ShiftModel> shifts = shiftRepository
                .findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        branchId, start, end);
        Map<Long, List<ShiftAssignmentModel>> assignmentsByShiftId = loadAssignmentsByShiftId(shifts);
        return buildWeeklySchedule(branchId, normalizedWeekStart, shifts, assignmentsByShiftId);
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

        // BM may assign freely; return all branch staff of the required role.
        return userRepository.findByBranchIdAndRoleName(branchId, targetRole.name()).stream()
                .filter(employee -> employee.getRole() == targetRole)
                .map(shiftMapper::toAvailableEmployeeResponse)
                .toList();
    }

    @Override
    public List<ShiftResponse> getMyShifts() {
        UserModel user = requireStaffViewer();
        LocalDateTime from = LocalDate.now().minusDays(7).atStartOfDay();
        LocalDateTime to = LocalDate.now().plusDays(14).atStartOfDay();
        List<ShiftAssignmentModel> assignments =
                assignmentRepository.findPublishedAssignmentsForStaffBetween(
                        user.getId(), from, to, ShiftStatus.PUBLISHED);
        List<ShiftResponse> responses = new ArrayList<>();
        for (ShiftAssignmentModel assignment : assignments) {
            ShiftModel shift = assignment.getShift();
            List<ShiftAssignmentModel> all = assignmentRepository.findByShiftId(shift.getId());
            responses.add(shiftMapper.toResponse(shift, all));
        }
        return responses;
    }

    @Override
    public WeeklyScheduleResponse getMyWeeklySchedule(LocalDate weekStart) {
        UserModel user = requireStaffViewer();
        if (user.getBranchId() == null) {
            throw new BusinessException("Your account is not assigned to a branch.");
        }
        LocalDate normalizedWeekStart = mondayOf(requireDate(weekStart, "Week start is required."));
        LocalDateTime from = normalizedWeekStart.atStartOfDay();
        LocalDateTime to = normalizedWeekStart.plusDays(7).atStartOfDay();
        List<ShiftAssignmentModel> myAssignments =
                assignmentRepository.findPublishedAssignmentsForStaffBetween(
                        user.getId(), from, to, ShiftStatus.PUBLISHED);

        List<ShiftModel> shifts = new ArrayList<>();
        Map<Long, ShiftModel> seen = new LinkedHashMap<>();
        for (ShiftAssignmentModel assignment : myAssignments) {
            ShiftModel shift = assignment.getShift();
            if (shift == null || shift.getId() == null) {
                continue;
            }
            if (seen.putIfAbsent(shift.getId(), shift) == null) {
                shifts.add(shift);
            }
        }
        Map<Long, List<ShiftAssignmentModel>> assignmentsByShiftId = loadAssignmentsByShiftId(shifts);
        WeeklyScheduleResponse response =
                buildWeeklySchedule(user.getBranchId(), normalizedWeekStart, shifts, assignmentsByShiftId);
        BranchModel branch = findBranchOrThrow(user.getBranchId());
        response.setOperatingHours(branch.getOperatingHours());
        return response;
    }

    private UserModel requireStaffViewer() {
        UserModel user = currentUserProvider.getCurrentUserOrThrow();
        UserRole role = user.getRole() == null ? null : user.getRole().toWebRole();
        if (role != UserRole.CASHIER && role != UserRole.INVENTORY_STAFF) {
            throw new BusinessException("Only cashiers and inventory staff can view assigned shifts.");
        }
        return user;
    }

    @Override
    @Transactional
    public ShiftResponse checkIn(Long shiftId) {
        UserModel user = currentUserProvider.getCurrentUserOrThrow();
        UserRole role = user.getRole() == null ? null : user.getRole().toWebRole();
        if (role != UserRole.CASHIER && role != UserRole.INVENTORY_STAFF) {
            throw new BusinessException("Only cashiers and inventory staff can check in to a shift.");
        }
        ShiftModel shift = findShiftOrThrow(shiftId);
        if (shift.getStatus() != ShiftStatus.PUBLISHED) {
            throw new BusinessException("Only published shifts can be checked in.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (shift.getStartTime() != null) {
            LocalDateTime windowOpen = shift.getStartTime().minusMinutes(30);
            if (now.isBefore(windowOpen)) {
                throw new BusinessException(
                        "Check-in opens 30 minutes before the shift starts. Too early for this shift.");
            }
            if (shift.getEndTime() != null && now.isAfter(shift.getEndTime())) {
                throw new BusinessException("This shift has already ended. Check-in is no longer available.");
            }
        }
        ShiftAssignmentModel assignment = assignmentRepository
                .findFirstByShiftIdAndStaffIdOrderByIdDesc(shiftId, user.getId())
                .orElseThrow(() -> new BusinessException("You are not assigned to this shift."));
        if (assignment.getCheckInAt() == null) {
            assignment.setCheckInAt(now);
            assignmentRepository.save(assignment);
        }
        List<ShiftAssignmentModel> all = assignmentRepository.findByShiftId(shiftId);
        return shiftMapper.toResponse(shift, all);
    }

    private List<ShiftAssignmentModel> syncSlotAssignments(
            ShiftModel shift,
            List<Long> cashiers,
            List<Long> inventoryStaff) {
        Set<Long> employeeIds = new LinkedHashSet<>();
        employeeIds.addAll(cashiers);
        employeeIds.addAll(inventoryStaff);
        Map<Long, UserModel> employeesById = userRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(UserModel::getId, employee -> employee));
        if (employeesById.size() != employeeIds.size()) {
            throw new NotFoundException("One or more selected employees were not found.");
        }

        AssignmentChanges changes = planSlotAssignmentSync(
                shift,
                cashiers,
                inventoryStaff,
                assignmentRepository.findByShiftId(shift.getId()),
                employeesById);
        if (!changes.toDelete().isEmpty()) {
            assignmentRepository.deleteAllInBatch(changes.toDelete());
        }
        if (!changes.toSave().isEmpty()) {
            assignmentRepository.saveAll(changes.toSave());
        }
        return changes.result();
    }

    private AssignmentChanges planSlotAssignmentSync(
            ShiftModel shift,
            List<Long> cashiers,
            List<Long> inventoryStaff,
            List<ShiftAssignmentModel> current,
            Map<Long, UserModel> employeesById) {

        ensureNoRoleOverlap(cashiers, inventoryStaff);
        Map<Long, UserRole> desired = new LinkedHashMap<>();
        for (Long id : cashiers) {
            desired.put(id, UserRole.CASHIER);
        }
        for (Long id : inventoryStaff) {
            desired.put(id, UserRole.INVENTORY_STAFF);
        }
        assertEmployeeCap(desired.size());
        validateDesiredEmployees(shift.getBranchId(), cashiers, inventoryStaff, employeesById);

        Set<Long> keep = new HashSet<>();
        List<ShiftAssignmentModel> toDelete = new ArrayList<>();
        List<ShiftAssignmentModel> toSave = new ArrayList<>();
        List<ShiftAssignmentModel> result = new ArrayList<>();

        for (ShiftAssignmentModel assignment : current) {
            Long staffId = assignment.getStaff().getId();
            UserRole wanted = desired.get(staffId);
            if (wanted == null) {
                toDelete.add(assignment);
                continue;
            }
            if (effectiveAssignmentRole(assignment) != wanted) {
                assignment.setAssignedRole(wanted.toWebRole());
                toSave.add(assignment);
            }
            keep.add(staffId);
            result.add(assignment);
        }

        for (Map.Entry<Long, UserRole> entry : desired.entrySet()) {
            if (keep.contains(entry.getKey())) {
                continue;
            }
            ShiftAssignmentModel assignment = new ShiftAssignmentModel();
            assignment.setShift(shift);
            assignment.setStaff(employeesById.get(entry.getKey()));
            assignment.setAssignedRole(entry.getValue().toWebRole());
            toSave.add(assignment);
            result.add(assignment);
        }
        return new AssignmentChanges(toDelete, toSave, result);
    }

    private void validateDesiredEmployees(
            Long branchId,
            List<Long> cashiers,
            List<Long> inventoryStaff,
            Map<Long, UserModel> employeesById) {

        for (Long id : cashiers) {
            UserModel employee = employeesById.get(id);
            if (employee == null) {
                throw new NotFoundException("Employee not found.");
            }
            if (!Objects.equals(employee.getBranchId(), branchId)) {
                throw new BusinessException("Employee must belong to the same branch as the shift.");
            }
            validateEmployeeRole(employee, UserRole.CASHIER);
        }
        for (Long id : inventoryStaff) {
            UserModel employee = employeesById.get(id);
            if (employee == null) {
                throw new NotFoundException("Employee not found.");
            }
            if (!Objects.equals(employee.getBranchId(), branchId)) {
                throw new BusinessException("Employee must belong to the same branch as the shift.");
            }
            validateEmployeeRole(employee, UserRole.INVENTORY_STAFF);
        }
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new));
    }

    private void ensureNoRoleOverlap(List<Long> cashiers, List<Long> inventoryStaff) {
        Set<Long> cashierSet = new HashSet<>(cashiers);
        for (Long id : inventoryStaff) {
            if (cashierSet.contains(id)) {
                throw new BusinessException("An employee cannot be assigned as both Cashier and Inventory Staff on the same slot.");
            }
        }
    }

    private void assertEmployeeCap(int count) {
        if (count > MAX_EMPLOYEES_PER_SHIFT) {
            throw new BusinessException(
                    "A shift can have at most " + MAX_EMPLOYEES_PER_SHIFT + " employees.");
        }
    }

    private String slotKey(LocalDateTime start, LocalDateTime end) {
        return start + "|" + end;
    }

    private record VirtualSlot(
            LocalDateTime start,
            LocalDateTime end,
            boolean first,
            boolean last,
            boolean published,
            ShiftModel existing) {
    }

    private record AssignmentChanges(
            List<ShiftAssignmentModel> toDelete,
            List<ShiftAssignmentModel> toSave,
            List<ShiftAssignmentModel> result) {
    }

    private String publishStaffingIssue(
            ShiftModel shift,
            List<ShiftAssignmentModel> assignments,
            List<ShiftModel> dayShifts) {
        try {
            validatePublishStaffing(shift, assignments, dayShifts);
            return null;
        } catch (BusinessException ex) {
            return ex.getMessage();
        }
    }

    private LocalDate mondayOf(LocalDate date) {
        int day = date.getDayOfWeek().getValue(); // Mon=1 … Sun=7
        return date.minusDays(day - 1L);
    }

    private String formatSlotConflict(LocalDateTime start, LocalDateTime end, String reason) {
        return start.toLocalDate() + " " + start.toLocalTime() + "–" + end.toLocalTime() + ": " + reason;
    }

    private ShiftResponse toResponse(ShiftModel shift) {
        return shiftMapper.toResponse(shift, assignmentRepository.findByShiftId(shift.getId()));
    }

    private WeeklyScheduleResponse buildWeeklySchedule(
            Long branchId,
            LocalDate weekStart,
            List<ShiftModel> shifts,
            Map<Long, List<ShiftAssignmentModel>> assignmentsByShiftId) {

        WeeklyScheduleResponse response = new WeeklyScheduleResponse();
        response.setBranchId(branchId);
        response.setWeekStart(weekStart);
        response.setDays(buildScheduleDays(weekStart, shifts, assignmentsByShiftId));
        return response;
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
            UserRole requiredRole) {

        if (!Objects.equals(employee.getBranchId(), shift.getBranchId())) {
            throw new BusinessException("Employee must belong to the same branch as the shift.");
        }

        validateEmployeeRole(employee, requiredRole);
    }

    private void validateEmployeeRole(UserModel employee, UserRole requiredRole) {
        UserRole targetRole = requireRole(requiredRole);
        UserRole actual = employee.getRole();
        if (actual == null || actual == UserRole.CUSTOMER || actual != targetRole) {
            throw new BusinessException("Employee does not match the required role for this shift.");
        }
    }

    private void validateShiftTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new BusinessException("Start time and end time are required.");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException("End time must be after start time.");
        }
        if (!startTime.toLocalDate().equals(endTime.toLocalDate())) {
            throw new BusinessException("Shifts must stay on the same calendar day (no overnight).");
        }
        long minutes = Duration.between(startTime, endTime).toMinutes();
        if (minutes > MAX_SHIFT_HOURS * 60L) {
            throw new BusinessException("Shift cannot exceed " + MAX_SHIFT_HOURS + " hours (part-time friendly).");
        }
    }

    private UserRole effectiveAssignmentRole(ShiftAssignmentModel assignment) {
        if (assignment.getAssignedRole() != null) {
            return assignment.getAssignedRole().toWebRole();
        }
        if (assignment.getStaff() != null && assignment.getStaff().getRole() != null) {
            return assignment.getStaff().getRole().toWebRole();
        }
        return null;
    }

    private void validatePublishStaffing(
            ShiftModel shift,
            List<ShiftAssignmentModel> assignments,
            List<ShiftModel> dayShifts) {

        assertEmployeeCap(assignments.size());
        if (assignments.isEmpty()) {
            throw new BusinessException("Every shift needs at least one employee before publishing.");
        }
        long cashierCount = assignments.stream()
                .filter(a -> effectiveAssignmentRole(a) == UserRole.CASHIER)
                .count();
        if (cashierCount < 1) {
            throw new BusinessException("Every shift needs at least one Cashier before publishing.");
        }

        ShiftModel firstShift = dayShifts.stream()
                .min(Comparator.comparing(ShiftModel::getStartTime))
                .orElse(null);
        ShiftModel lastShift = dayShifts.stream()
                .max(Comparator.comparing(ShiftModel::getStartTime))
                .orElse(null);
        boolean isFirst = firstShift != null && Objects.equals(firstShift.getId(), shift.getId());
        boolean isLast = lastShift != null && Objects.equals(lastShift.getId(), shift.getId());

        if (isFirst || isLast) {
            long isCount = assignments.stream()
                    .filter(a -> effectiveAssignmentRole(a) == UserRole.INVENTORY_STAFF)
                    .count();
            if (isCount < 1) {
                String which = isFirst && isLast ? "first/last" : (isFirst ? "first" : "last");
                throw new BusinessException(
                        "The " + which + " shift of the day needs at least one Inventory Staff before publishing.");
            }
        }
    }

    private void validateNoOverlappingShift(
            Long branchId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long excludedShiftId) {

        if (shiftRepository.existsOverlapping(branchId, startTime, endTime, excludedShiftId)) {
            throw new BusinessException("Shift already exists in this time range.");
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

    // =========================================================================
    // Opening cash float
    // =========================================================================

    /**
     * The first slot of the day takes the float the BM supplied, falling back to the
     * configured default. Later slots start at 0 and are overwritten by the cash handover
     * recorded on the previous shift session when it is approved (see the shift-session flow).
     */
    private BigDecimal resolveOpeningCash(BigDecimal requested, boolean firstOfDay) {
        if (!firstOfDay) {
            return BigDecimal.ZERO;
        }
        return requested != null ? requested : defaultOpeningCash;
    }

    /** True when the window matches the branch's first derived slot template of the day. */
    private boolean isFirstTemplateSlot(
            BranchModel branch,
            LocalDateTime startTime,
            LocalDateTime endTime) {

        List<ShiftSlotDeriver.SlotTemplate> templates =
                ShiftSlotDeriver.derive(branch.getOperatingHours());
        if (templates.isEmpty()) {
            return false;
        }
        ShiftSlotDeriver.SlotTemplate first = templates.get(0);
        return first.start().equals(startTime.toLocalTime())
                && first.end().equals(endTime.toLocalTime());
    }

    /**
     * Manual create path only: slot templates do not apply there, so the day's first shift is
     * simply the earliest-starting one — the same rule {@link #validatePublishStaffing} uses.
     */
    private boolean isEarliestShiftOfDay(Long branchId, LocalDateTime startTime) {
        LocalDate day = startTime.toLocalDate();
        return shiftRepository
                .findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        branchId, day.atStartOfDay(), day.plusDays(1).atStartOfDay())
                .stream()
                .noneMatch(existing -> existing.getStartTime().isBefore(startTime));
    }

    private void assertOpeningCashEditable(ShiftModel shift) {
        boolean editable = shift.getStatus() == ShiftStatus.DRAFT
                || shift.getStatus() == ShiftStatus.PUBLISHED;
        if (!editable) {
            throw new BusinessException(
                    "Opening cash can only be changed while the shift is DRAFT or PUBLISHED. "
                            + "Current status: " + shift.getStatus());
        }
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

    private BranchModel findBranchOrThrow(Long branchId) {
        return branchRepository.findById(branchId)
                .orElseThrow(() -> new NotFoundException("Branch not found."));
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
