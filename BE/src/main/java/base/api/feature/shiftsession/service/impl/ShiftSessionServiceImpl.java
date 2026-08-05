package base.api.feature.shiftsession.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.inventorycount.repository.InventoryCountSessionRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.shift.repository.ShiftAssignmentRepository;
import base.api.feature.posorder.repository.PaymentRepository;
import base.api.feature.shift.repository.ShiftRepository;
import base.api.feature.shiftsession.dto.request.ReconcileShiftSessionRequest;
import base.api.feature.shiftsession.dto.request.CloseInventoryShiftRequest;
import base.api.feature.shiftsession.dto.request.ConfirmHandoverRequest;
import base.api.feature.shiftsession.dto.request.ConfirmOpeningFundRequest;
import base.api.feature.shiftsession.dto.request.ConfirmVerificationRequest;
import base.api.feature.shiftsession.dto.request.SaveClosingDraftRequest;
import base.api.feature.shiftsession.dto.request.StartShiftRequest;
import base.api.feature.shiftsession.dto.response.HighValueItemResponse;
import base.api.feature.shiftsession.dto.response.InventoryClosingSummaryResponse;
import base.api.feature.shiftsession.dto.response.ShiftSessionTransactionSummaryResponse;
import base.api.feature.shiftsession.dto.response.ShiftSessionApprovalHistoryResponse;
import base.api.feature.shiftsession.dto.response.ShiftBriefResponse;
import base.api.feature.shiftsession.dto.response.ShiftSessionResponse;
import base.api.feature.shiftsession.repository.ShiftSessionApprovalRepository;
import base.api.feature.shiftsession.repository.ShiftSessionHighValueItemRepository;
import base.api.feature.shiftsession.repository.ShiftSessionRepository;
import base.api.feature.shiftsession.service.IShiftSessionService;
import base.api.shared.entity.BranchInventoryModel;
import base.api.shared.entity.CategoryModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.ShiftAssignmentModel;
import base.api.shared.entity.ShiftModel;
import base.api.shared.entity.ShiftSessionApprovalModel;
import base.api.shared.entity.ShiftSessionHighValueItemModel;
import base.api.shared.entity.ShiftSessionModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.CashDifferenceStatus;
import base.api.shared.enums.ShiftSessionApprovalDecision;
import base.api.shared.enums.ShiftSessionStatus;
import base.api.shared.enums.ShiftStatus;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BusinessException;
import base.api.shared.security.CurrentUserProvider;
import base.api.shared.security.DemoAccounts;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShiftSessionServiceImpl implements IShiftSessionService {

    private static final BigDecimal HIGH_VALUE_PRICE_THRESHOLD = new BigDecimal("500000");
    private static final int HIGH_VALUE_MAX_ITEMS = 20;
    private static final BigDecimal STANDARD_OPENING_FUND = new BigDecimal("2000000");
    private static final String BRANCH_ALREADY_OPEN_MESSAGE =
            "Another cashier is currently operating an active shift in this branch.";
    private static final List<ShiftSessionStatus> CASHIER_ACTIVE_STATUSES = List.of(
            ShiftSessionStatus.OPEN, ShiftSessionStatus.CLOSING, ShiftSessionStatus.PENDING_HANDOVER);
    private static final List<ShiftSessionStatus> HANDOVER_FUND_STATUSES = List.of(
            ShiftSessionStatus.COMPLETED,
            ShiftSessionStatus.APPROVED,
            ShiftSessionStatus.CLOSED);

    @Autowired
    private ShiftSessionRepository sessionRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ShiftSessionHighValueItemRepository highValueItemRepository;

    @Autowired
    private ShiftSessionApprovalRepository approvalRepository;

    @Autowired
    private ShiftAssignmentRepository assignmentRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IBranchRepository branchRepository;

    @Autowired
    private BranchInventoryRepository branchInventoryRepository;

    @Autowired
    private IProductRepository productRepository;

    @Autowired
    private InventoryCountSessionRepository inventoryCountSessionRepository;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public ShiftSessionResponse getCurrent() {
        UserModel user = requireStaff();
        Optional<ShiftSessionModel> active = findActiveSession(user.getId());
        if (active.isPresent()) {
            ShiftSessionModel session = active.get();
            // Demo: abandon stuck close flow so cashier can open a fresh POS shift anytime.
            if (DemoAccounts.isDemoBypassEmail(user.getEmail())
                    && (session.getStatus() == ShiftSessionStatus.CLOSING
                            || session.getStatus() == ShiftSessionStatus.PENDING_HANDOVER)) {
                session.setStatus(ShiftSessionStatus.COMPLETED);
                if (session.getClosedAt() == null) {
                    session.setClosedAt(LocalDateTime.now());
                }
                sessionRepository.save(session);
            } else {
                return toResponse(session, user);
            }
        }
        ShiftAssignmentModel assignment = resolveCurrentAssignment(user).orElse(null);
        if (assignment == null) {
            ShiftSessionResponse empty = new ShiftSessionResponse();
            empty.setEmployeeId(user.getId());
            empty.setRole(user.getRole());
            empty.setBranchId(user.getBranchId());
            empty.setEmployeeName(formatName(user));
            return empty;
        }
        ShiftSessionModel session = getOrCreateScheduledSession(user, assignment);
        return toResponse(session, user);
    }

    @Override
    public ShiftSessionResponse getOpeningContext() {
        return getCurrent();
    }

    @Override
    @Transactional
    public ShiftSessionResponse confirmOpeningFund(ConfirmOpeningFundRequest request) {
        UserModel user = requireCashier();
        ShiftAssignmentModel assignment = requireCurrentAssignment(user);
        ensureCheckedIn(assignment);
        ShiftSessionModel session = getOrCreateScheduledSession(user, assignment);
        assertStatus(session, ShiftSessionStatus.SCHEDULED);
        populateOpeningFund(session, assignment.getShift());
        session.setOpeningConfirmed(true);
        if (request != null && request.getNote() != null) {
            session.setOpeningNote(request.getNote().trim());
        }
        session.setOpeningFundReceivedAt(LocalDateTime.now());
        sessionRepository.save(session);
        return toResponse(session, user);
    }

    @Override
    @Transactional
    public ShiftSessionResponse startShift(StartShiftRequest request) {
        UserModel user = requireStaff();
        ShiftAssignmentModel assignment = requireCurrentAssignment(user);
        ensureCheckedIn(assignment);
        ShiftSessionModel session = getOrCreateScheduledSession(user, assignment);
        if (session.getStatus() == ShiftSessionStatus.OPEN) {
            return toResponse(session, user);
        }
        if (session.getStatus() != ShiftSessionStatus.SCHEDULED) {
            throw new BusinessException("Shift cannot be started in its current state.");
        }
        boolean confirmed = request != null && Boolean.TRUE.equals(request.getConfirmedReceived());
        if (user.getRole() == UserRole.CASHIER && !confirmed && !Boolean.TRUE.equals(session.getOpeningConfirmed())) {
            throw new BusinessException(
                    "You must confirm that you have received the opening fund before opening the shift.");
        }
        assertNoOtherOpenSessionInBranch(session.getBranchId(), user.getId(), user);
        populateOpeningFund(session, assignment.getShift());
        session.setOpeningConfirmed(true);
        if (request != null && request.getNote() != null && !request.getNote().isBlank()) {
            session.setOpeningNote(request.getNote().trim());
        }
        LocalDateTime now = LocalDateTime.now();
        session.setOpeningFundReceivedAt(now);
        session.setStatus(ShiftSessionStatus.OPEN);
        session.setOpenedAt(now);
        if (session.getShiftAssignmentId() == null) {
            session.setShiftAssignmentId(assignment.getId());
        }
        sessionRepository.save(session);

        return toResponse(session, user);
    }

    @Override
    public ShiftSessionResponse getClosingContext() {
        UserModel user = requireStaff();
        ShiftSessionModel session = requireClosingSession(user);
        refreshCashierTotals(session);
        ensureHighValueItems(session);
        if (session.getStatus() == ShiftSessionStatus.OPEN) {
            session.setStatus(ShiftSessionStatus.CLOSING);
        }
        if (user.getRole() == UserRole.CASHIER) {
            resolveHandoverTarget(session);
        }
        sessionRepository.save(session);
        return toResponse(session, user);
    }

    @Override
    @Transactional
    public ShiftSessionResponse confirmVerification(ConfirmVerificationRequest request) {
        UserModel user = requireCashier();
        ShiftSessionModel session = requireClosingSession(user);
        List<ShiftSessionHighValueItemModel> existing =
                highValueItemRepository.findBySessionIdOrderByIdAsc(session.getId());
        Map<Integer, ShiftSessionHighValueItemModel> byProduct = existing.stream()
                .collect(Collectors.toMap(ShiftSessionHighValueItemModel::getProductId, Function.identity()));

        for (ConfirmVerificationRequest.HighValueLineRequest line : request.getItems()) {
            ShiftSessionHighValueItemModel row = byProduct.get(line.getProductId());
            if (row == null) {
                throw new BusinessException("Unknown high-value product in verification.");
            }
            row.setActualQty(line.getActualQty());
            row.setDifference(line.getActualQty() - row.getExpectedQty());
        }
        highValueItemRepository.saveAll(existing);
        session.setVerificationConfirmed(true);
        sessionRepository.save(session);
        return toResponse(session, user);
    }

    @Override
    @Transactional
    public ShiftSessionResponse confirmHandover(ConfirmHandoverRequest request) {
        UserModel user = requireCashier();
        ShiftSessionModel session = requireClosingSession(user);
        if (!Boolean.TRUE.equals(session.getVerificationConfirmed())) {
            throw new BusinessException("Complete high-value verification before handover.");
        }
        refreshCashierTotals(session);
        BigDecimal actual = request.getActualCash();
        if (actual == null) {
            throw new BusinessException("Actual cash is required.");
        }
        session.setActualCash(actual);
        session.setDifference(calculateDifference(session.getExpectedCash(), actual));
        if (session.getDifference() != null
                && session.getDifference().compareTo(BigDecimal.ZERO) != 0
                && (request.getRemark() == null || request.getRemark().isBlank())) {
            throw new BusinessException("Remark is required when there is a cash difference.");
        }
        session.setHandoverRemark(request.getRemark());
        session.setHandoverConfirmed(true);
        if (session.getStatus() != ShiftSessionStatus.CLOSING) {
            session.setStatus(ShiftSessionStatus.CLOSING);
        }
        sessionRepository.save(session);
        return toResponse(session, user);
    }

    @Override
    @Transactional
    public ShiftSessionResponse saveClosingDraft(SaveClosingDraftRequest request) {
        UserModel user = requireStaff();
        ShiftSessionModel session = requireClosingSession(user);
        if (request.getActualCash() != null) {
            session.setActualCash(request.getActualCash());
            refreshCashierTotals(session);
            session.setDifference(calculateDifference(session.getExpectedCash(), request.getActualCash()));
        }
        if (request.getHandoverRemark() != null) {
            session.setHandoverRemark(request.getHandoverRemark());
        }
        if (request.getClosingNote() != null) {
            session.setClosingNote(request.getClosingNote());
        }
        if (request.getAdjustedProductsCount() != null) {
            session.setAdjustedProductsCount(request.getAdjustedProductsCount());
        }
        if (request.getDamagedProductsCount() != null) {
            session.setDamagedProductsCount(request.getDamagedProductsCount());
        }
        if (request.getMissingProductsCount() != null) {
            session.setMissingProductsCount(request.getMissingProductsCount());
        }
        sessionRepository.save(session);
        return toResponse(session, user);
    }

    @Override
    @Transactional
    public ShiftSessionResponse closeCashierShift() {
        UserModel user = requireCashier();
        ShiftSessionModel session = requireClosingSession(user);
        if (!Boolean.TRUE.equals(session.getVerificationConfirmed())
                || !Boolean.TRUE.equals(session.getHandoverConfirmed())) {
            throw new BusinessException("Complete verification and handover before closing the shift.");
        }
        refreshCashierTotals(session);
        if (session.getActualCash() == null) {
            throw new BusinessException("Actual cash is required.");
        }
        session.setDifference(calculateDifference(session.getExpectedCash(), session.getActualCash()));
        BigDecimal diff = session.getDifference() != null ? session.getDifference() : BigDecimal.ZERO;
        if (diff.compareTo(BigDecimal.ZERO) != 0
                && (session.getHandoverRemark() == null || session.getHandoverRemark().isBlank())) {
            throw new BusinessException("An explanation is required when there is a cash difference.");
        }
        session.setClosedAt(LocalDateTime.now());
        if (diff.compareTo(BigDecimal.ZERO) == 0) {
            session.setStatus(ShiftSessionStatus.COMPLETED);
        } else {
            session.setStatus(ShiftSessionStatus.PENDING_APPROVAL);
        }
        sessionRepository.save(session);

        ShiftModel shift = shiftRepository.findById(session.getShiftId()).orElseThrow();
        shift.setExpectedCash(session.getExpectedCash());
        shift.setActualCash(session.getActualCash());
        shift.setDifference(session.getDifference());
        shiftRepository.save(shift);

        assignmentRepository.findFirstByShiftIdAndStaffIdOrderByIdDesc(session.getShiftId(), user.getId())
                .ifPresent(a -> {
                    a.setCheckOutAt(LocalDateTime.now());
                    assignmentRepository.save(a);
                });
        return toResponse(session, user);
    }

    @Override
    @Transactional
    public ShiftSessionResponse closeInventoryShift(CloseInventoryShiftRequest request) {
        UserModel user = requireInventoryStaff();
        ShiftSessionModel session = requireOpenSession(user);
        session.setAdjustedProductsCount(
                request.getAdjustedProductsCount() != null ? request.getAdjustedProductsCount() : 0);
        session.setDamagedProductsCount(
                request.getDamagedProductsCount() != null ? request.getDamagedProductsCount() : 0);
        session.setMissingProductsCount(
                request.getMissingProductsCount() != null ? request.getMissingProductsCount() : 0);
        session.setClosingNote(request.getClosingNote());
        finalizeClose(session, user);
        assignmentRepository.findFirstByShiftIdAndStaffIdOrderByIdDesc(session.getShiftId(), user.getId())
                .ifPresent(a -> {
                    a.setCheckOutAt(LocalDateTime.now());
                    assignmentRepository.save(a);
                });
        return toResponse(session, user);
    }

    @Override
    public List<ShiftSessionResponse> getHistory() {
        UserModel user = requireStaff();
        return sessionRepository.findByEmployeeIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(session -> toResponse(session, user))
                .toList();
    }

    @Override
    public List<ShiftSessionResponse> listBranchSessionsForManager() {
        UserModel manager = requireBranchManager();
        List<ShiftSessionStatus> statuses = List.of(
                ShiftSessionStatus.OPEN,
                ShiftSessionStatus.CLOSING,
                ShiftSessionStatus.PENDING_HANDOVER,
                ShiftSessionStatus.PENDING_APPROVAL);
        return sessionRepository
                .findByBranchIdAndStatusInOrderByOpenedAtDesc(manager.getBranchId(), statuses)
                .stream()
                .map(session -> toResponse(session, manager))
                .toList();
    }

    @Override
    public List<ShiftSessionResponse> listPendingReconciliation() {
        UserModel manager = requireBranchManager();
        return sessionRepository
                .findByBranchIdAndStatusOrderByClosedAtDesc(
                        manager.getBranchId(), ShiftSessionStatus.PENDING_APPROVAL)
                .stream()
                .map(session -> toResponse(session, manager))
                .toList();
    }

    @Override
    public ShiftSessionResponse getReconciliationDetail(Long sessionId) {
        UserModel manager = requireBranchManager();
        ShiftSessionModel session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("Shift session not found."));
        assertSameBranch(manager, session);
        ShiftSessionResponse response = toResponse(session, manager);
        response.setTransactionSummary(buildTransactionSummary(session));
        return response;
    }

    @Override
    @Transactional
    public ShiftSessionResponse decideReconciliation(Long sessionId, ReconcileShiftSessionRequest request) {
        UserModel manager = requireBranchManager();
        ShiftSessionModel session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("Shift session not found."));
        assertSameBranch(manager, session);
        if (session.getStatus() != ShiftSessionStatus.PENDING_APPROVAL) {
            throw new BusinessException("This shift session is not awaiting cash reconciliation approval.");
        }
        if (request.getApproved() == null) {
            throw new BusinessException("Approval decision is required.");
        }

        boolean approved = Boolean.TRUE.equals(request.getApproved());
        String note = request.getNote() != null ? request.getNote().trim() : "";
        if (!approved && note.isBlank()) {
            throw new BusinessException("Manager note is required when rejecting a cash difference.");
        }

        ShiftSessionApprovalDecision decision = approved
                ? ShiftSessionApprovalDecision.APPROVED
                : ShiftSessionApprovalDecision.REJECTED;

        LocalDateTime now = LocalDateTime.now();
        ShiftSessionApprovalModel row = new ShiftSessionApprovalModel();
        row.setSessionId(session.getId());
        row.setDecision(decision);
        row.setNote(note.isBlank() ? (approved ? "Approved." : "Rejected.") : note);
        row.setDecidedBy(manager.getId());
        approvalRepository.save(row);

        session.setApprovedBy(manager.getId());
        session.setApprovedAt(now);
        session.setManagerNote(note.isBlank() ? null : note);

        if (approved) {
            session.setStatus(ShiftSessionStatus.COMPLETED);
        } else {
            session.setStatus(ShiftSessionStatus.REJECTED);
            session.setHandoverConfirmed(false);
        }
        sessionRepository.save(session);
        ShiftSessionResponse response = toResponse(session, manager);
        response.setTransactionSummary(buildTransactionSummary(session));
        return response;
    }

    private void finalizeClose(ShiftSessionModel session, UserModel user) {
        session.setStatus(ShiftSessionStatus.COMPLETED);
        session.setClosedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    private UserModel requireStaff() {
        UserModel user = currentUserProvider.getCurrentUserOrThrow();
        if (user.getRole() != UserRole.CASHIER) {
            throw new BusinessException("Only cashiers can use shift sessions.");
        }
        if (user.getBranchId() == null) {
            throw new BusinessException("Your account is not assigned to a branch.");
        }
        return user;
    }

    private UserModel requireCashier() {
        return requireStaff();
    }

    private UserModel requireBranchManager() {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole role = currentUserProvider.getCurrentUserRole();
        if (role != UserRole.BRANCH_MANAGER) {
            throw new BusinessException("Only branch managers can approve cash discrepancies.");
        }
        if (currentUser.getBranchId() == null) {
            throw new BusinessException("Branch manager is not assigned to a branch.");
        }
        return currentUser;
    }

    private UserModel requireInventoryStaff() {
        throw new BusinessException("This action is for inventory staff only.");
    }

    private Optional<ShiftSessionModel> findActiveSession(Long employeeId) {
        return sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(
                employeeId, CASHIER_ACTIVE_STATUSES);
    }

    private ShiftSessionModel requireOpenSession(UserModel user) {
        return sessionRepository
                .findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(user.getId(), CASHIER_ACTIVE_STATUSES)
                .orElseThrow(() -> new BusinessException("No open shift session. Start your shift first."));
    }

    private ShiftSessionModel requireClosingSession(UserModel user) {
        Optional<ShiftSessionModel> active = findActiveSession(user.getId());
        if (active.isPresent()) {
            return active.get();
        }
        return sessionRepository
                .findFirstByEmployeeIdAndStatusOrderByClosedAtDesc(
                        user.getId(), ShiftSessionStatus.REJECTED)
                .map(session -> {
                    session.setStatus(ShiftSessionStatus.CLOSING);
                    session.setHandoverConfirmed(false);
                    return sessionRepository.save(session);
                })
                .orElseThrow(() -> new BusinessException("No open shift session. Start your shift first."));
    }

    private void assertNoOtherOpenSessionInBranch(Long branchId, Long employeeId, UserModel actor) {
        if (actor != null && DemoAccounts.isDemoBypassEmail(actor.getEmail())) {
            return;
        }
        sessionRepository
                .findFirstByBranchIdAndStatusOrderByOpenedAtDesc(branchId, ShiftSessionStatus.OPEN)
                .ifPresent(open -> {
                    if (!Objects.equals(open.getEmployeeId(), employeeId)) {
                        throw new BusinessException(BRANCH_ALREADY_OPEN_MESSAGE);
                    }
                });
    }

    private void assertNoOtherOpenSessionInBranch(Long branchId, Long employeeId) {
        assertNoOtherOpenSessionInBranch(branchId, employeeId, null);
    }

    private Optional<ShiftAssignmentModel> resolveCurrentAssignment(UserModel user) {
        // Demo cashiers/IS: always ensure a published slot covering "now" (bypass BM schedule).
        if (DemoAccounts.isDemoBypassEmail(user.getEmail())) {
            return Optional.of(ensureDemoAssignment(user));
        }

        LocalDateTime now = LocalDateTime.now();
        List<ShiftAssignmentModel> overlapping = assignmentRepository.findPublishedAssignmentsOverlapping(
                user.getId(),
                now.minusMinutes(30),
                now.plusMinutes(30),
                ShiftStatus.PUBLISHED);
        if (!overlapping.isEmpty()) {
            return Optional.of(overlapping.get(0));
        }
        Optional<ShiftSessionModel> open = sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(
                user.getId(), CASHIER_ACTIVE_STATUSES);
        if (open.isPresent()) {
            return assignmentRepository.findFirstByShiftIdAndStaffIdOrderByIdDesc(
                    open.get().getShiftId(), user.getId());
        }
        LocalDate today = now.toLocalDate();
        List<ShiftAssignmentModel> todayAssignments =
                assignmentRepository.findPublishedAssignmentsForStaffBetween(
                        user.getId(),
                        today.atStartOfDay(),
                        today.plusDays(1).atStartOfDay(),
                        ShiftStatus.PUBLISHED);
        return todayAssignments.stream().findFirst();
    }

    /**
     * Demo cashiers may open a POS shift without a BM-published assignment.
     * Creates (or reuses) a published slot that covers the current time (max 6 hours).
     */
    private ShiftAssignmentModel ensureDemoAssignment(UserModel user) {
        if (user.getBranchId() == null) {
            throw new BusinessException("Demo account is not assigned to a branch.");
        }
        LocalDateTime now = LocalDateTime.now();
        List<ShiftAssignmentModel> overlapping = assignmentRepository.findPublishedAssignmentsOverlapping(
                user.getId(),
                now.minusMinutes(30),
                now.plusMinutes(30),
                ShiftStatus.PUBLISHED);
        if (!overlapping.isEmpty()) {
            return overlapping.get(0);
        }

        LocalDateTime start = now.minusMinutes(15);
        LocalDateTime end = start.plusHours(6);

        ShiftModel shift = new ShiftModel();
        shift.setBranchId(user.getBranchId());
        shift.setCreatedBy(user.getId());
        shift.setStartTime(start);
        shift.setEndTime(end);
        shift.setOpeningCash(STANDARD_OPENING_FUND);
        shift.setExpectedCash(BigDecimal.ZERO);
        shift.setStatus(ShiftStatus.PUBLISHED);
        shift.setApprovedBy(user.getId());
        shift = shiftRepository.save(shift);

        ShiftAssignmentModel assignment = new ShiftAssignmentModel();
        assignment.setShift(shift);
        assignment.setStaff(user);
        assignment.setAssignedRole(
                user.getRole() == UserRole.INVENTORY_STAFF ? UserRole.INVENTORY_STAFF : UserRole.CASHIER);
        assignment.setCheckInAt(now);
        return assignmentRepository.save(assignment);
    }

    private ShiftAssignmentModel requireCurrentAssignment(UserModel user) {
        return resolveCurrentAssignment(user)
                .orElseThrow(() -> new BusinessException("No published shift is assigned to you for this time."));
    }

    private ShiftSessionModel getOrCreateScheduledSession(UserModel user, ShiftAssignmentModel assignment) {
        ShiftModel shift = assignment.getShift();
        return sessionRepository
                .findFirstByShiftIdAndEmployeeIdOrderByIdDesc(shift.getId(), user.getId())
                .orElseGet(() -> {
                    ShiftSessionModel session = new ShiftSessionModel();
                    session.setShiftId(shift.getId());
                    session.setShiftAssignmentId(assignment.getId());
                    session.setEmployeeId(user.getId());
                    session.setRole(user.getRole());
                    session.setBranchId(shift.getBranchId());
                    session.setStatus(ShiftSessionStatus.SCHEDULED);
                    populateOpeningFund(session, shift);
                    return sessionRepository.save(session);
                });
    }

    private void populateOpeningFund(ShiftSessionModel session, ShiftModel shift) {
        if (session.getRole() != UserRole.CASHIER) {
            return;
        }
        if (isFirstPublishedShiftOfDay(shift)) {
            session.setOpeningFundAmount(STANDARD_OPENING_FUND);
        } else if (session.getOpeningFundAmount() == null) {
            session.setOpeningFundAmount(
                    shift.getOpeningCash() != null ? shift.getOpeningCash() : BigDecimal.ZERO);
        }
        session.setOpeningFundReceivedFrom(shift.getCreatedBy());
        if (session.getOpeningFundReceivedAt() == null) {
            session.setOpeningFundReceivedAt(
                    shift.getStartTime() != null ? shift.getStartTime() : LocalDateTime.now());
        }
    }

    private void ensureCheckedIn(ShiftAssignmentModel assignment) {
        if (assignment.getCheckInAt() == null) {
            assignment.setCheckInAt(LocalDateTime.now());
            assignmentRepository.save(assignment);
        }
    }

    private boolean isFirstPublishedShiftOfDay(ShiftModel shift) {
        LocalDate day = shift.getStartTime().toLocalDate();
        List<ShiftModel> dayShifts = shiftRepository
                .findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        shift.getBranchId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        return dayShifts.stream()
                .filter(s -> s.getStatus() == ShiftStatus.PUBLISHED)
                .findFirst()
                .map(first -> Objects.equals(first.getId(), shift.getId()))
                .orElse(true);
    }

    private void applyAssignmentContext(ShiftSessionResponse response, Long shiftId, Long employeeId) {
        if (shiftId == null || employeeId == null) {
            return;
        }
        assignmentRepository.findFirstByShiftIdAndStaffIdOrderByIdDesc(shiftId, employeeId)
                .ifPresent(a -> {
                    response.setCheckedIn(a.getCheckInAt() != null);
                    response.setCheckInAt(a.getCheckInAt());
                });
    }

    /**
     * Expected = tiền đầu ca + doanh thu tiền mặt trong ca − tiền hoàn.
     *
     * Doanh thu đọc thẳng từ bảng payments của ca thay vì tin vào số đã lưu trên
     * session: thu ngân vẫn bán tiếp sau khi mở màn đóng ca, nên số phải được tính
     * lại ở mọi lần chạm vào (xem closing context, lưu nháp, bàn giao).
     */
    private void refreshCashierTotals(ShiftSessionModel session) {
        if (session.getRole() != UserRole.CASHIER) {
            return;
        }
        BigDecimal sales = BigDecimal.ZERO;
        if (session.getShiftId() != null) {
            BigDecimal taken = paymentRepository.sumCashTakenInShift(session.getShiftId());
            sales = taken != null ? taken : BigDecimal.ZERO;
            session.setTransactionCount(
                    (int) paymentRepository.countTransactionsInShift(session.getShiftId()));
        }
        session.setCashSales(sales);

        BigDecimal opening = session.getOpeningFundAmount() != null
                ? session.getOpeningFundAmount()
                : BigDecimal.ZERO;
        // Chưa có luồng hoàn tiền nào trong app nên refunds luôn 0; giữ lại vế trừ
        // để khi có refund thì chỉ cần điền số vào, không phải sửa công thức.
        BigDecimal refunds = session.getRefundAmount() != null ? session.getRefundAmount() : BigDecimal.ZERO;
        session.setExpectedCash(opening.add(sales).subtract(refunds));
    }

    private void ensureHighValueItems(ShiftSessionModel session) {
        if (session.getRole() != UserRole.CASHIER) {
            return;
        }
        List<ShiftSessionHighValueItemModel> existing =
                highValueItemRepository.findBySessionIdOrderByIdAsc(session.getId());
        if (!existing.isEmpty()) {
            return;
        }
        List<BranchInventoryModel> inventory = branchInventoryRepository.findByBranchId(session.getBranchId());
        Set<Integer> productIds = inventory.stream()
                .map(BranchInventoryModel::getProductId)
                .collect(Collectors.toSet());
        Map<Integer, ProductModel> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity()));

        List<ShiftSessionHighValueItemModel> seeds = new ArrayList<>();
        for (BranchInventoryModel row : inventory) {
            ProductModel product = products.get(row.getProductId());
            if (product == null || product.getDefaultSalePrice() == null) {
                continue;
            }
            if (product.getDefaultSalePrice().compareTo(HIGH_VALUE_PRICE_THRESHOLD) < 0) {
                continue;
            }
            ShiftSessionHighValueItemModel item = new ShiftSessionHighValueItemModel();
            item.setSessionId(session.getId());
            item.setProductId(product.getId());
            item.setExpectedQty(row.getCurrentStock() != null ? row.getCurrentStock() : 0);
            seeds.add(item);
        }
        seeds.sort(Comparator.comparing(
                i -> products.get(i.getProductId()).getDefaultSalePrice(), Comparator.reverseOrder()));
        if (seeds.size() > HIGH_VALUE_MAX_ITEMS) {
            seeds = seeds.subList(0, HIGH_VALUE_MAX_ITEMS);
        }
        highValueItemRepository.saveAll(seeds);
    }

    private void resolveHandoverTarget(ShiftSessionModel session) {
        ShiftModel shift = shiftRepository.findById(session.getShiftId()).orElseThrow();
        List<ShiftModel> sameDay = shiftRepository
                .findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        shift.getBranchId(),
                        shift.getStartTime().toLocalDate().atStartOfDay(),
                        shift.getStartTime().toLocalDate().plusDays(1).atStartOfDay());
        ShiftModel nextShift = sameDay.stream()
                .filter(s -> s.getStatus() == ShiftStatus.PUBLISHED)
                .filter(s -> s.getStartTime().isAfter(shift.getEndTime())
                        || s.getStartTime().equals(shift.getEndTime()))
                .findFirst()
                .orElse(null);
        if (nextShift != null) {
            assignmentRepository.findByShiftId(nextShift.getId()).stream()
                    .filter(a -> effectiveRole(a) == UserRole.CASHIER)
                    .findFirst()
                    .ifPresent(a -> session.setHandoverToEmployeeId(a.getStaff().getId()));
        } else {
            session.setHandoverToEmployeeId(shift.getCreatedBy());
        }
    }

    private UserRole effectiveRole(ShiftAssignmentModel assignment) {
        if (assignment.getAssignedRole() != null) {
            return assignment.getAssignedRole();
        }
        return assignment.getStaff().getRole();
    }

    private InventoryClosingSummaryResponse buildInventorySummary(ShiftSessionModel session) {
        InventoryClosingSummaryResponse summary = new InventoryClosingSummaryResponse();
        List<BranchInventoryModel> rows = branchInventoryRepository.findByBranchId(session.getBranchId());
        summary.setTotalSku(rows.size());
        summary.setLowStockSku((int) rows.stream()
                .filter(r -> r.getCurrentStock() != null && r.getCurrentStock() <= 5)
                .count());
        LocalDateTime from = session.getOpenedAt() != null ? session.getOpenedAt() : LocalDateTime.now().minusHours(4);
        int counts = (int) inventoryCountSessionRepository
                .findByBranchIdOrderByCreatedAtDesc(session.getBranchId())
                .stream()
                .filter(s -> s.getCreatedAt() != null
                        && !s.getCreatedAt().isBefore(from)
                        && !s.getCreatedAt().isAfter(LocalDateTime.now()))
                .count();
        summary.setCountSessionsDuringShift(counts);
        return summary;
    }

    private void assertStatus(ShiftSessionModel session, ShiftSessionStatus expected) {
        if (session.getStatus() != expected) {
            throw new BusinessException("Shift session is not in the expected state.");
        }
    }

    private BigDecimal calculateDifference(BigDecimal expected, BigDecimal actual) {
        if (expected == null || actual == null) {
            return null;
        }
        return actual.subtract(expected);
    }

    private ShiftSessionResponse toResponse(ShiftSessionModel session, UserModel viewer) {
        UserModel sessionEmployee = userRepository.findById(session.getEmployeeId()).orElse(viewer);

        ShiftSessionResponse response = new ShiftSessionResponse();
        response.setId(session.getId());
        response.setShiftId(session.getShiftId());
        response.setShiftAssignmentId(session.getShiftAssignmentId());
        response.setEmployeeId(session.getEmployeeId());
        if (session.getOpenedAt() != null) {
            response.setOpenedBy(session.getEmployeeId());
        }
        response.setRole(session.getRole());
        response.setBranchId(session.getBranchId());
        response.setStatus(session.getStatus());
        response.setOpenedAt(session.getOpenedAt());
        response.setClosedAt(session.getClosedAt());
        response.setOpeningConfirmed(session.getOpeningConfirmed());
        response.setOpeningFundConfirmed(session.getOpeningConfirmed());
        response.setVerificationConfirmed(session.getVerificationConfirmed());
        response.setHandoverConfirmed(session.getHandoverConfirmed());
        response.setOpeningNote(session.getOpeningNote());
        response.setClosingNote(session.getClosingNote());
        response.setOpeningFundAmount(session.getOpeningFundAmount());
        response.setOpeningFundReceivedAt(session.getOpeningFundReceivedAt());
        response.setTransactionCount(session.getTransactionCount());
        response.setCashSales(session.getCashSales());
        response.setRefundAmount(session.getRefundAmount());
        response.setExpectedCash(session.getExpectedCash());
        response.setActualCash(session.getActualCash());
        response.setDifference(session.getDifference());
        response.setDifferenceStatus(resolveDifferenceStatus(session.getDifference()));
        response.setCashierExplanation(session.getHandoverRemark());
        response.setHandoverToEmployeeId(session.getHandoverToEmployeeId());
        response.setHandoverRemark(session.getHandoverRemark());
        response.setAdjustedProductsCount(session.getAdjustedProductsCount());
        response.setDamagedProductsCount(session.getDamagedProductsCount());
        response.setMissingProductsCount(session.getMissingProductsCount());
        response.setEmployeeName(formatName(sessionEmployee));
        response.setApprovedBy(session.getApprovedBy());
        response.setApprovedAt(session.getApprovedAt());
        response.setManagerNote(session.getManagerNote());
        if (session.getApprovedBy() != null) {
            userRepository.findById(session.getApprovedBy())
                    .ifPresent(u -> response.setApprovedByName(formatName(u)));
        }
        if (session.getManagerNote() != null) {
            response.setReviewNote(session.getManagerNote());
        }
        if (session.getApprovedBy() != null) {
            response.setReviewedByName(response.getApprovedByName());
        }

        if (session.getOpeningFundReceivedFrom() != null) {
            userRepository.findById(session.getOpeningFundReceivedFrom())
                    .ifPresent(u -> response.setOpeningFundReceivedFromName(formatName(u)));
        }
        if (session.getHandoverToEmployeeId() != null) {
            userRepository.findById(session.getHandoverToEmployeeId())
                    .ifPresent(u -> response.setHandoverToEmployeeName(formatName(u)));
        }

        branchRepository.findById(session.getBranchId())
                .ifPresent(b -> response.setBranchName(b.getName()));

        shiftRepository.findById(session.getShiftId()).ifPresent(shift -> {
            ShiftBriefResponse brief = new ShiftBriefResponse();
            brief.setId(shift.getId());
            brief.setStartTime(shift.getStartTime());
            brief.setEndTime(shift.getEndTime());
            brief.setShiftNumber(shiftNumberForDay(shift));
            brief.setOpeningCash(shift.getOpeningCash());
            response.setShift(brief);
        });

        applyAssignmentContext(response, session.getShiftId(), session.getEmployeeId());

        if (session.getRole() == UserRole.CASHIER && session.getId() != null) {
            List<ShiftSessionHighValueItemModel> items =
                    highValueItemRepository.findBySessionIdOrderByIdAsc(session.getId());
            response.setHighValueItems(mapHighValueItems(items));
        }
        if (session.getId() != null) {
            response.setApprovalHistory(mapApprovalHistory(session.getId()));
        }
        return response;
    }

    private List<ShiftSessionApprovalHistoryResponse> mapApprovalHistory(Long sessionId) {
        return approvalRepository.findBySessionIdOrderByDecidedAtDesc(sessionId).stream()
                .map(row -> {
                    ShiftSessionApprovalHistoryResponse item = new ShiftSessionApprovalHistoryResponse();
                    item.setId(row.getId());
                    item.setDecision(row.getDecision());
                    item.setNote(row.getNote());
                    item.setDecidedBy(row.getDecidedBy());
                    item.setDecidedAt(row.getDecidedAt());
                    userRepository.findById(row.getDecidedBy())
                            .ifPresent(u -> item.setDecidedByName(formatName(u)));
                    return item;
                })
                .toList();
    }

    private CashDifferenceStatus resolveDifferenceStatus(BigDecimal difference) {
        if (difference == null) {
            return null;
        }
        int cmp = difference.compareTo(BigDecimal.ZERO);
        if (cmp == 0) {
            return CashDifferenceStatus.BALANCED;
        }
        return cmp < 0 ? CashDifferenceStatus.CASH_SHORTAGE : CashDifferenceStatus.CASH_EXCESS;
    }

    private ShiftSessionTransactionSummaryResponse buildTransactionSummary(ShiftSessionModel session) {
        ShiftSessionTransactionSummaryResponse summary = new ShiftSessionTransactionSummaryResponse();
        LocalDateTime from = session.getOpenedAt() != null ? session.getOpenedAt() : session.getCreatedAt();
        LocalDateTime to = session.getClosedAt() != null ? session.getClosedAt() : LocalDateTime.now();
        if (from == null) {
            from = LocalDateTime.now().minusHours(8);
        }
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    """
                    SELECT
                      COUNT(DISTINCT o.id) AS totalOrders,
                      SUM(CASE WHEN LOWER(COALESCE(p.method, '')) = 'cash' THEN 1 ELSE 0 END) AS cashOrders,
                      SUM(CASE WHEN p.method IS NOT NULL AND LOWER(p.method) <> 'cash' THEN 1 ELSE 0 END) AS cardOrders,
                      SUM(CASE WHEN LOWER(COALESCE(o.status, '')) LIKE '%refund%' THEN 1 ELSE 0 END) AS refundOrders,
                      SUM(CASE WHEN LOWER(COALESCE(o.status, '')) LIKE '%cancel%' THEN 1 ELSE 0 END) AS cancelledOrders
                    FROM orders o
                    LEFT JOIN payments p ON p.order_id = o.id
                    WHERE o.shift_id = ? AND o.cashier_id = ?
                      AND o.created_at >= ? AND o.created_at <= ?
                    """,
                    session.getShiftId(),
                    session.getEmployeeId(),
                    from,
                    to);
            summary.setTotalOrders(toInt(row.get("totalOrders")));
            summary.setCashOrders(toInt(row.get("cashOrders")));
            summary.setCardOrders(toInt(row.get("cardOrders")));
            summary.setRefundOrders(toInt(row.get("refundOrders")));
            summary.setCancelledOrders(toInt(row.get("cancelledOrders")));
            return summary;
        } catch (Exception ex) {
            int total = session.getTransactionCount() != null ? session.getTransactionCount() : 0;
            int refundHint = session.getRefundAmount() != null
                    && session.getRefundAmount().compareTo(BigDecimal.ZERO) > 0
                            ? 1
                            : 0;
            summary.setTotalOrders(total);
            summary.setCashOrders(total);
            summary.setCardOrders(0);
            summary.setRefundOrders(refundHint);
            summary.setCancelledOrders(0);
            return summary;
        }
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private void assertSameBranch(UserModel manager, ShiftSessionModel session) {
        if (!Objects.equals(manager.getBranchId(), session.getBranchId())) {
            throw new BusinessException("Shift session belongs to another branch.");
        }
    }

    private List<HighValueItemResponse> mapHighValueItems(List<ShiftSessionHighValueItemModel> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        Set<Integer> productIds = items.stream()
                .map(ShiftSessionHighValueItemModel::getProductId)
                .collect(Collectors.toSet());
        Map<Integer, ProductModel> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity()));
        List<HighValueItemResponse> rows = new ArrayList<>();
        for (ShiftSessionHighValueItemModel item : items) {
            ProductModel product = products.get(item.getProductId());
            HighValueItemResponse row = new HighValueItemResponse();
            row.setProductId(item.getProductId());
            row.setExpectedQty(item.getExpectedQty());
            row.setActualQty(item.getActualQty());
            row.setDifference(item.getDifference());
            if (product != null) {
                row.setProductName(product.getName());
                CategoryModel category = product.getCategory();
                if (category != null) {
                    row.setCategoryName(category.getName());
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private int shiftNumberForDay(ShiftModel shift) {
        LocalDate day = shift.getStartTime().toLocalDate();
        List<ShiftModel> dayShifts = shiftRepository
                .findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        shift.getBranchId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        for (int i = 0; i < dayShifts.size(); i++) {
            if (Objects.equals(dayShifts.get(i).getId(), shift.getId())) {
                return i + 1;
            }
        }
        return 1;
    }

    private String formatName(UserModel user) {
        String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String last = user.getLastName() != null ? user.getLastName().trim() : "";
        String combined = (first + " " + last).trim();
        return combined.isEmpty() ? user.getUserName() : combined;
    }
}
