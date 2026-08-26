package base.api.feature.shiftsession.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.inventorycount.repository.InventoryCountSessionRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductSalePriceService;
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
import base.api.feature.shiftsession.dto.response.ShiftHandoverCandidateResponse;
import base.api.feature.shiftsession.dto.response.PreviousShiftHandoverReportResponse;
import base.api.feature.shiftsession.dto.response.PreviousShiftProductVarianceResponse;
import base.api.feature.shiftsession.dto.response.ShiftSessionResponse;
import base.api.feature.shiftsession.dto.response.BranchAttendanceResponse;
import base.api.feature.shiftsession.dto.response.BranchRefundResponse;
import base.api.feature.shiftsession.repository.ShiftSessionApprovalRepository;
import base.api.feature.shiftsession.repository.ShiftSessionHighValueItemRepository;
import base.api.feature.shiftsession.repository.ShiftSessionRepository;
import base.api.feature.shiftsession.service.IShiftSessionService;
import base.api.feature.shift.util.ShiftSlotDeriver;
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
import base.api.shared.enums.FundTransferMethod;
import base.api.shared.enums.ShiftSessionApprovalDecision;
import base.api.shared.enums.ShiftSessionStatus;
import base.api.shared.enums.ShiftStatus;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BusinessException;
import base.api.shared.security.CurrentUserProvider;
import base.api.shared.security.DemoAccounts;
import base.api.shared.config.EmailService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShiftSessionServiceImpl implements IShiftSessionService {

    private static final BigDecimal HIGH_VALUE_PRICE_THRESHOLD = new BigDecimal("500000");
    /** Lower bar for theft-prone categories (tobacco, cosmetics, cards, premium alcohol). */
    private static final BigDecimal HIGH_RISK_CATEGORY_PRICE_THRESHOLD = new BigDecimal("300000");
    private static final int HIGH_VALUE_MAX_ITEMS = 30;
    private static final BigDecimal STANDARD_OPENING_FUND = new BigDecimal("2000000");
    private static final String BRANCH_ALREADY_OPEN_MESSAGE =
            "Another cashier is currently operating an active shift in this branch.";
    private static final String JOINED_SHIFT_NOTE_PREFIX = "Joined shift opened by ";
    private static final String AUTO_CLOSE_NOTE =
            "Auto-closed by system: cashier did not complete closing within the grace period after shift end time.";
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
    private ProductSalePriceService productSalePriceService;

    @Autowired
    private InventoryCountSessionRepository inventoryCountSessionRepository;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmailService emailService;

    @Autowired
    private base.api.feature.posorder.repository.OrderRefundRepository orderRefundRepository;

    @Autowired
    private base.api.feature.posorder.repository.OrderRepository orderRepository;

    @Value("${shift.test-mode.allow-outside-hours:false}")
    private boolean allowOutsideHoursTestMode;

    @Value("${shift.auto-close.grace-minutes:30}")
    private int autoCloseGraceMinutes;

    @Value("${url.client-url:http://localhost:5175}")
    private String clientUrl;

    @Override
    @Transactional
    public ShiftSessionResponse getCurrent() {
        UserModel user = requireStaff();
        Optional<ShiftSessionModel> active = findActiveSession(user.getId());
        if (active.isPresent()) {
            ShiftSessionModel session = active.get();
            if (autoCloseIfOverdue(session)) {
                // Session was stale; continue so the cashier can pick up the current assignment.
            } else {
                ShiftSessionResponse response = toResponse(session, user);
                applySlotContext(response, user);
                return response;
            }
        }
        ShiftAssignmentModel assignment = resolveCurrentAssignment(user).orElse(null);
        if (assignment == null) {
            ShiftSessionResponse empty = new ShiftSessionResponse();
            empty.setEmployeeId(user.getId());
            empty.setRole(user.getRole());
            empty.setBranchId(user.getBranchId());
            empty.setEmployeeName(formatName(user));
            applySlotContext(empty, user);
            return empty;
        }
        ShiftSessionModel session = getOrCreateScheduledSession(user, assignment);
        if (session.getStatus() == ShiftSessionStatus.SCHEDULED && session.getRole() == UserRole.CASHIER) {
            shiftRepository.findById(session.getShiftId()).ifPresent(shift -> {
                populateOpeningFund(session, shift);
                sessionRepository.save(session);
            });
            Optional<ShiftSessionModel> colleagueOpen =
                    findColleagueOpenSessionOnShift(session.getShiftId(), user.getId());
            if (colleagueOpen.isPresent()) {
                applyJoinSession(session, colleagueOpen.get(), user);
                sessionRepository.save(session);
                ShiftSessionResponse response = toResponse(session, user);
                enrichJoinResponse(response, colleagueOpen.get());
                applySlotContext(response, user);
                return response;
            }
        }
        ShiftSessionResponse response = toResponse(session, user);
        applySlotContext(response, user);
        return response;
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
        assertCanBeginOpening(session);
        populateOpeningFund(session, assignment.getShift());
        applyOpeningFundReceipt(
                session,
                request != null ? request.getReceivedFromEmployeeId() : null,
                request != null ? request.getFundMethod() : null);
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
        ShiftSessionModel session = resolveSessionForStart(user, assignment);
        if (session.getStatus() == ShiftSessionStatus.OPEN) {
            return toResponse(session, user);
        }
        assertCanBeginOpening(session);
        Optional<ShiftSessionModel> colleagueOpen =
                findColleagueOpenSessionOnShift(session.getShiftId(), user.getId());
        if (colleagueOpen.isPresent()) {
            applyJoinSession(session, colleagueOpen.get(), user);
            if (session.getShiftAssignmentId() == null) {
                session.setShiftAssignmentId(assignment.getId());
            }
            sessionRepository.save(session);
            ShiftSessionResponse response = toResponse(session, user);
            enrichJoinResponse(response, colleagueOpen.get());
            return response;
        }
        boolean confirmed = request != null && Boolean.TRUE.equals(request.getConfirmedReceived());
        if (user.getRole() == UserRole.CASHIER && !confirmed && !Boolean.TRUE.equals(session.getOpeningConfirmed())) {
            throw new BusinessException(
                    "You must confirm that you have received the opening fund before opening the shift.");
        }
        assertNoConflictingOpenSessionInBranch(session.getBranchId(), session.getShiftId(), user.getId(), user);
        populateOpeningFund(session, assignment.getShift());
        applyOpeningFundReceipt(
                session,
                request != null ? request.getReceivedFromEmployeeId() : null,
                request != null ? request.getFundMethod() : null);
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
    @Transactional
    public ShiftSessionResponse getClosingContext() {
        UserModel user = requireStaff();
        ShiftSessionModel session = requireClosingSession(user);
        if (autoCloseIfOverdue(session)) {
            throw new BusinessException(
                    "This shift was automatically closed because it ended more than "
                            + autoCloseGraceMinutes
                            + " minutes ago without a manual close. "
                            + "Your branch manager has been notified to review the closing.");
        }
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
        ShiftHandoverCandidateResponse target = resolveSelectedHandoverTarget(
                session, request.getHandoverToEmployeeId());
        if (isEarlyClose(session) && !Boolean.TRUE.equals(target.getScheduledReplacement())) {
            throw new BusinessException(
                    "Early closing requires a scheduled replacement cashier for the current time.");
        }
        session.setHandoverRemark(request.getRemark());
        session.setHandoverToEmployeeId(target.getEmployeeId());
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
        if (requiresReconciliationApproval(session)) {
            session.setStatus(ShiftSessionStatus.PENDING_APPROVAL);
        } else {
            session.setStatus(ShiftSessionStatus.COMPLETED);
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
    public int autoCloseOverdueSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(autoCloseGraceMinutes);
        List<ShiftSessionModel> overdue =
                sessionRepository.findOverdueCashierSessions(CASHIER_ACTIVE_STATUSES, cutoff);
        int closed = 0;
        for (ShiftSessionModel session : overdue) {
            if (session.getRole() != UserRole.CASHIER) {
                continue;
            }
            if (performAutoClose(session)) {
                closed++;
            }
        }
        return closed;
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
    @Transactional
    public List<ShiftSessionResponse> getHistory() {
        return getHistoryPage(new base.api.shared.dto.PageRequestDTO(1, 20, null, "createdAt", "desc"))
                .getListObjects();
    }

    @Override
    @Transactional
    public base.api.shared.dto.PageResponseDTO<ShiftSessionResponse> getHistoryPage(
            base.api.shared.dto.PageRequestDTO pageRequest) {
        UserModel user = requireStaff();
        purgeFutureClosedSessions();
        LocalDateTime now = LocalDateTime.now();
        org.springframework.data.domain.Pageable pageable = pageRequest.toPageable(
                "createdAt",
                org.springframework.data.domain.Sort.Direction.DESC,
                java.util.Set.of("createdAt", "openedAt", "closedAt", "id"));
        org.springframework.data.domain.Page<ShiftSessionModel> page =
                sessionRepository.findPastHistoryByEmployeeId(user.getId(), now, pageable);
        List<ShiftSessionModel> sessions = page.getContent();
        List<ShiftSessionResponse> mapped = mapHistoryBatch(sessions, user);
        return new base.api.shared.dto.PageResponseDTO<>(
                mapped,
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious());
    }

    @Override
    @Transactional
    public int purgeFutureClosedSessions() {
        List<ShiftSessionStatus> closedFamily = List.of(
                ShiftSessionStatus.COMPLETED,
                ShiftSessionStatus.CLOSED,
                ShiftSessionStatus.PENDING_APPROVAL,
                ShiftSessionStatus.APPROVED,
                ShiftSessionStatus.REJECTED);
        List<ShiftSessionModel> futureClosed =
                sessionRepository.findFutureClosedSessions(closedFamily, LocalDateTime.now());
        if (futureClosed.isEmpty()) {
            return 0;
        }
        for (ShiftSessionModel session : futureClosed) {
            highValueItemRepository.deleteBySessionId(session.getId());
            approvalRepository.deleteBySessionId(session.getId());
        }
        sessionRepository.deleteAll(futureClosed);
        return futureClosed.size();
    }

    private List<ShiftSessionResponse> mapHistoryBatch(List<ShiftSessionModel> sessions, UserModel viewer) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        Set<Long> shiftIds = sessions.stream()
                .map(ShiftSessionModel::getShiftId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> userIds = new HashSet<>();
        for (ShiftSessionModel session : sessions) {
            if (session.getOpeningFundReceivedFrom() != null) {
                userIds.add(session.getOpeningFundReceivedFrom());
            }
            if (session.getHandoverToEmployeeId() != null) {
                userIds.add(session.getHandoverToEmployeeId());
            }
        }
        Map<Long, ShiftModel> shiftsById = shiftIds.isEmpty()
                ? Map.of()
                : shiftRepository.findAllById(shiftIds).stream()
                        .collect(Collectors.toMap(ShiftModel::getId, Function.identity(), (a, b) -> a));
        Map<Long, UserModel> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(UserModel::getId, Function.identity(), (a, b) -> a));
        List<ShiftSessionResponse> out = new ArrayList<>(sessions.size());
        for (ShiftSessionModel session : sessions) {
            out.add(toHistoryResponse(session, viewer, shiftsById, usersById));
        }
        return out;
    }

    /** List view: stored totals only — no high-value items, handover candidates, or extra user lookups. */
    private ShiftSessionResponse toHistoryResponse(ShiftSessionModel session, UserModel viewer) {
        return toHistoryResponse(session, viewer, Map.of(), Map.of());
    }

    private ShiftSessionResponse toHistoryResponse(
            ShiftSessionModel session,
            UserModel viewer,
            Map<Long, ShiftModel> shiftsById,
            Map<Long, UserModel> usersById) {
        ShiftSessionResponse response = new ShiftSessionResponse();
        response.setId(session.getId());
        response.setShiftId(session.getShiftId());
        response.setEmployeeId(session.getEmployeeId());
        response.setRole(session.getRole());
        response.setBranchId(session.getBranchId());
        response.setStatus(session.getStatus());
        response.setOpenedAt(session.getOpenedAt());
        response.setClosedAt(session.getClosedAt());
        response.setOpeningFundAmount(session.getOpeningFundAmount());
        response.setOpeningFundMethod(session.getOpeningFundMethod());
        response.setTransactionCount(session.getTransactionCount());
        response.setCashSales(session.getCashSales());
        response.setRefundAmount(session.getRefundAmount());
        response.setExpectedCash(session.getExpectedCash());
        response.setActualCash(session.getActualCash());
        response.setDifference(session.getDifference());
        response.setDifferenceStatus(resolveDifferenceStatus(session.getDifference()));
        response.setEmployeeName(formatName(viewer));
        if (session.getOpeningFundReceivedFrom() != null) {
            UserModel from = usersById.get(session.getOpeningFundReceivedFrom());
            if (from == null) {
                from = userRepository.findById(session.getOpeningFundReceivedFrom()).orElse(null);
            }
            if (from != null) {
                response.setOpeningFundReceivedFromName(formatName(from));
            }
        }
        if (session.getHandoverToEmployeeId() != null) {
            UserModel to = usersById.get(session.getHandoverToEmployeeId());
            if (to == null) {
                to = userRepository.findById(session.getHandoverToEmployeeId()).orElse(null);
            }
            if (to != null) {
                response.setHandoverToEmployeeName(formatName(to));
            }
        }
        ShiftModel shift = shiftsById.get(session.getShiftId());
        if (shift == null && session.getShiftId() != null) {
            shift = shiftRepository.findById(session.getShiftId()).orElse(null);
        }
        if (shift != null) {
            ShiftBriefResponse brief = new ShiftBriefResponse();
            brief.setId(shift.getId());
            brief.setStartTime(shift.getStartTime());
            brief.setEndTime(shift.getEndTime());
            brief.setShiftNumber(shiftNumberForDay(shift));
            brief.setOpeningCash(shift.getOpeningCash());
            response.setShift(brief);
        }
        return response;
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
    @Transactional
    public List<ShiftSessionResponse> listPendingReconciliation() {
        return listReconciliation("with", null);
    }

    @Override
    @Transactional
    public List<ShiftSessionResponse> listReconciliation(String discrepancyFilter, String statusFilter) {
        UserModel manager = requireBranchManager();
        finalizeBalancedPendingSessions(manager.getBranchId());

        String discrepancy = discrepancyFilter == null || discrepancyFilter.isBlank()
                ? "with"
                : discrepancyFilter.trim().toLowerCase(Locale.ROOT);
        if (!"with".equals(discrepancy) && !"without".equals(discrepancy) && !"all".equals(discrepancy)) {
            throw new BusinessException("Invalid discrepancy filter. Use with, without, or all.");
        }

        LinkedHashMap<Long, ShiftSessionModel> byId = new LinkedHashMap<>();
        if ("with".equals(discrepancy) || "all".equals(discrepancy)) {
            ShiftSessionStatus withStatus = resolveReconciliationStatus(
                    statusFilter, ShiftSessionStatus.PENDING_APPROVAL);
            for (ShiftSessionModel session : sessionRepository.findPendingReconciliationWithDifference(
                    manager.getBranchId(), withStatus)) {
                byId.put(session.getId(), session);
            }
        }
        if ("without".equals(discrepancy) || "all".equals(discrepancy)) {
            List<ShiftSessionStatus> withoutStatuses = resolveBalancedStatuses(statusFilter);
            LocalDateTime since = LocalDateTime.now().minusDays(30);
            for (ShiftSessionModel session : sessionRepository.findBalancedClosedSessions(
                    manager.getBranchId(), withoutStatuses, since)) {
                byId.putIfAbsent(session.getId(), session);
            }
        }

        return byId.values().stream()
                .sorted(Comparator.comparing(
                        ShiftSessionModel::getClosedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(session -> toResponse(session, manager))
                .toList();
    }

    private ShiftSessionStatus resolveReconciliationStatus(
            String statusFilter,
            ShiftSessionStatus defaultStatus
    ) {
        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                return ShiftSessionStatus.valueOf(statusFilter.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("Invalid status filter: " + statusFilter);
            }
        }
        return defaultStatus;
    }

    private List<ShiftSessionStatus> resolveBalancedStatuses(String statusFilter) {
        if (statusFilter != null && !statusFilter.isBlank()) {
            return List.of(resolveReconciliationStatus(statusFilter, ShiftSessionStatus.COMPLETED));
        }
        return List.of(
                ShiftSessionStatus.COMPLETED,
                ShiftSessionStatus.CLOSED,
                ShiftSessionStatus.APPROVED,
                ShiftSessionStatus.PENDING_APPROVAL);
    }

    @Override
    @Transactional
    public ShiftSessionResponse getReconciliationDetail(Long sessionId) {
        UserModel manager = requireBranchManager();
        ShiftSessionModel session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("Shift session not found."));
        assertSameBranch(manager, session);
        if (session.getStatus() == ShiftSessionStatus.PENDING_APPROVAL
                && !requiresReconciliationApproval(session)) {
            session.setStatus(ShiftSessionStatus.COMPLETED);
            sessionRepository.save(session);
            throw new BusinessException("This shift had no cash or product difference and does not require reconciliation.");
        }
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
        if (!requiresReconciliationApproval(session)) {
            session.setStatus(ShiftSessionStatus.COMPLETED);
            sessionRepository.save(session);
            throw new BusinessException("This shift had no cash or product difference and does not require reconciliation.");
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

    @Override
    public List<BranchAttendanceResponse> listBranchAttendance(LocalDate from, LocalDate to) {
        UserModel manager = requireBranchManager();
        LocalDate effectiveTo = to == null ? LocalDate.now() : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.minusDays(7) : from;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessException("from date must not be after to date.");
        }

        LocalDateTime rangeStart = effectiveFrom.atStartOfDay();
        LocalDateTime rangeEnd = effectiveTo.plusDays(1).atStartOfDay();
        List<ShiftModel> shifts = shiftRepository
                .findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        manager.getBranchId(), rangeStart, rangeEnd);
        if (shifts.isEmpty()) {
            return List.of();
        }

        Map<Long, ShiftModel> shiftsById = shifts.stream()
                .collect(Collectors.toMap(ShiftModel::getId, Function.identity(), (a, b) -> a));
        List<ShiftAssignmentModel> assignments = assignmentRepository.findByShiftIdIn(shiftsById.keySet());
        List<BranchAttendanceResponse> rows = new ArrayList<>();
        for (ShiftAssignmentModel assignment : assignments) {
            ShiftModel shift = assignment.getShift();
            if (shift == null) {
                continue;
            }
            UserModel staff = assignment.getStaff();
            if (staff == null) {
                continue;
            }
            Optional<ShiftSessionModel> sessionOpt = sessionRepository
                    .findFirstByShiftIdAndEmployeeIdOrderByIdDesc(shift.getId(), staff.getId());

            BranchAttendanceResponse row = new BranchAttendanceResponse();
            row.setShiftId(shift.getId());
            row.setAssignmentId(assignment.getId());
            row.setEmployeeId(staff.getId());
            row.setCashierName(formatName(staff));
            row.setRole(assignment.getAssignedRole() == null
                    ? null
                    : assignment.getAssignedRole().name());
            row.setShiftStartTime(shift.getStartTime());
            row.setShiftEndTime(shift.getEndTime());
            row.setCheckInAt(assignment.getCheckInAt());
            sessionOpt.ifPresent(session -> {
                row.setSessionId(session.getId());
                row.setOpenedAt(session.getOpenedAt());
                row.setClosedAt(session.getClosedAt());
            });
            LocalDateTime attendanceAt = assignment.getCheckInAt() != null
                    ? assignment.getCheckInAt()
                    : row.getOpenedAt();
            if (attendanceAt != null && shift.getStartTime() != null) {
                row.setMinutesLate(Duration.between(shift.getStartTime(), attendanceAt).toMinutes());
            }
            rows.add(row);
        }
        rows.sort(Comparator
                .comparing(BranchAttendanceResponse::getShiftStartTime,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(BranchAttendanceResponse::getCashierName,
                        Comparator.nullsLast(String::compareToIgnoreCase)));
        return rows;
    }

    @Override
    public List<BranchRefundResponse> listBranchRefunds(LocalDate from, LocalDate to) {
        UserModel manager = requireBranchManager();
        LocalDate effectiveTo = to == null ? LocalDate.now() : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.minusDays(30) : from;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessException("from date must not be after to date.");
        }
        LocalDateTime rangeStart = effectiveFrom.atStartOfDay();
        LocalDateTime rangeEnd = effectiveTo.plusDays(1).atStartOfDay();

        List<base.api.shared.entity.OrderRefundModel> refunds =
                orderRefundRepository.findByBranchIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        manager.getBranchId(), "APPROVED", rangeStart, rangeEnd);
        if (refunds.isEmpty()) {
            return List.of();
        }

        Set<Long> orderIds = refunds.stream()
                .map(base.api.shared.entity.OrderRefundModel::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, base.api.shared.entity.OrderModel> ordersById = orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(base.api.shared.entity.OrderModel::getId, Function.identity(), (a, b) -> a));

        Set<Long> cashierIds = new HashSet<>();
        for (base.api.shared.entity.OrderRefundModel refund : refunds) {
            if (refund.getRequestedBy() != null) {
                cashierIds.add(refund.getRequestedBy());
            }
            base.api.shared.entity.OrderModel order = ordersById.get(refund.getOrderId());
            if (order != null && order.getCashierId() != null) {
                cashierIds.add(order.getCashierId());
            }
        }
        Map<Long, UserModel> usersById = cashierIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(cashierIds).stream()
                        .collect(Collectors.toMap(UserModel::getId, Function.identity(), (a, b) -> a));

        List<BranchRefundResponse> rows = new ArrayList<>();
        for (base.api.shared.entity.OrderRefundModel refund : refunds) {
            base.api.shared.entity.OrderModel order = ordersById.get(refund.getOrderId());
            Long cashierId = order != null && order.getCashierId() != null
                    ? order.getCashierId()
                    : refund.getRequestedBy();
            UserModel cashier = cashierId == null ? null : usersById.get(cashierId);

            BranchRefundResponse row = new BranchRefundResponse();
            row.setRefundId(refund.getId());
            row.setOrderId(refund.getOrderId());
            row.setInvoiceCode(order == null ? null : order.getInvoiceCode());
            row.setCashierId(cashierId);
            row.setCashierName(cashier == null ? null : formatName(cashier));
            row.setAmount(order == null ? null : order.getTotal());
            row.setRefundedAt(refund.getReviewedAt() != null ? refund.getReviewedAt() : refund.getCreatedAt());
            row.setReason(refund.getReason());
            row.setStatus(refund.getStatus());
            rows.add(row);
        }
        return rows;
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

    private void assertNoConflictingOpenSessionInBranch(
            Long branchId, Long shiftId, Long employeeId, UserModel actor) {
        if (actor != null && DemoAccounts.isDemoCashierBypassEmail(actor.getEmail())) {
            return;
        }
        sessionRepository
                .findFirstByBranchIdAndStatusOrderByOpenedAtDesc(branchId, ShiftSessionStatus.OPEN)
                .ifPresent(open -> {
                    if (!Objects.equals(open.getEmployeeId(), employeeId)
                            && !Objects.equals(open.getShiftId(), shiftId)) {
                        throw new BusinessException(BRANCH_ALREADY_OPEN_MESSAGE);
                    }
                });
    }

    private Optional<ShiftSessionModel> findColleagueOpenSessionOnShift(Long shiftId, Long employeeId) {
        if (shiftId == null || employeeId == null) {
            return Optional.empty();
        }
        return sessionRepository.findFirstByShiftIdAndStatusAndEmployeeIdNotOrderByOpenedAtAsc(
                shiftId, ShiftSessionStatus.OPEN, employeeId);
    }

    private void applyJoinSession(ShiftSessionModel session, ShiftSessionModel opener, UserModel user) {
        LocalDateTime now = LocalDateTime.now();
        session.setStatus(ShiftSessionStatus.OPEN);
        session.setOpenedAt(now);
        session.setOpeningConfirmed(true);
        session.setOpeningFundAmount(BigDecimal.ZERO);
        if (opener.getOpeningFundReceivedFrom() != null) {
            session.setOpeningFundReceivedFrom(opener.getOpeningFundReceivedFrom());
        }
        if (opener.getOpeningFundMethod() != null) {
            session.setOpeningFundMethod(opener.getOpeningFundMethod());
        }
        userRepository.findById(opener.getEmployeeId()).ifPresent(openerUser -> session.setOpeningNote(
                JOINED_SHIFT_NOTE_PREFIX + formatName(openerUser)));
        if (session.getOpeningFundReceivedAt() == null) {
            session.setOpeningFundReceivedAt(now);
        }
    }

    private void enrichJoinResponse(ShiftSessionResponse response, ShiftSessionModel opener) {
        if (response == null || opener == null) {
            return;
        }
        response.setJoinedExistingShift(true);
        response.setShiftOpenedByEmployeeId(opener.getEmployeeId());
        userRepository.findById(opener.getEmployeeId())
                .ifPresent(u -> response.setShiftOpenedByName(formatName(u)));
    }

    private boolean requiresReconciliationApproval(ShiftSessionModel session) {
        if (hasCashDifference(session.getDifference())) {
            return true;
        }
        if (session.getId() == null) {
            return false;
        }
        return hasProductCountDiscrepancy(session.getId());
    }

    private boolean hasProductCountDiscrepancy(Long sessionId) {
        return highValueItemRepository.findBySessionIdOrderByIdAsc(sessionId).stream()
                .anyMatch(item -> item.getDifference() != null && item.getDifference() != 0);
    }

    private void applyProductDiscrepancyFlags(
            ShiftSessionResponse response, List<ShiftSessionHighValueItemModel> items) {
        if (response == null || items == null || items.isEmpty()) {
            if (response != null) {
                response.setHasProductDiscrepancy(false);
                response.setProductDiscrepancyCount(0);
            }
            return;
        }
        long count = items.stream()
                .filter(item -> item.getDifference() != null && item.getDifference() != 0)
                .count();
        response.setHasProductDiscrepancy(count > 0);
        response.setProductDiscrepancyCount((int) count);
    }

    private void assertNoOtherOpenSessionInBranch(Long branchId, Long employeeId, UserModel actor) {
        assertNoConflictingOpenSessionInBranch(branchId, null, employeeId, actor);
    }

    private void assertNoOtherOpenSessionInBranch(Long branchId, Long employeeId) {
        assertNoOtherOpenSessionInBranch(branchId, employeeId, null);
    }

    private Optional<ShiftAssignmentModel> resolveCurrentAssignment(UserModel user) {
        // Demo cashiers/IS: always ensure a published slot covering "now" (bypass BM schedule).
        if (DemoAccounts.isDemoCashierBypassEmail(user.getEmail())) {
            return Optional.of(ensureDemoAssignment(user));
        }

        LocalDateTime now = LocalDateTime.now();
        Optional<ShiftSessionModel> open = sessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(
                user.getId(), CASHIER_ACTIVE_STATUSES);
        if (open.isPresent()) {
            return assignmentRepository.findFirstByShiftIdAndStaffIdOrderByIdDesc(
                    open.get().getShiftId(), user.getId());
        }
        if (user.getBranchId() == null) {
            return Optional.empty();
        }

        String operatingHours = branchRepository.findById(user.getBranchId())
                .map(branch -> branch.getOperatingHours())
                .orElse("06:00 - 23:00");
        List<ShiftSlotDeriver.SlotTemplate> slots = ShiftSlotDeriver.derive(operatingHours);
        LocalTime nowTime = now.toLocalTime();
        int slotIndex = ShiftSlotDeriver.indexForTime(slots, nowTime);
        if (slotIndex < 0) {
            if (!allowOutsideHoursTestMode) {
                return Optional.empty();
            }
            slotIndex = ShiftSlotDeriver.nearestSlotIndex(slots, nowTime);
        }
        ShiftSlotDeriver.SlotTemplate slot = slots.get(slotIndex);
        LocalDate today = now.toLocalDate();

        List<ShiftAssignmentModel> todayAssignments = assignmentRepository.findPublishedAssignmentsForStaffBetween(
                user.getId(),
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                ShiftStatus.PUBLISHED);

        Optional<ShiftAssignmentModel> exactSlot = todayAssignments.stream()
                .filter(assignment -> shiftMatchesSlot(assignment.getShift(), slot, today))
                .findFirst();
        if (exactSlot.isPresent()) {
            return exactSlot;
        }

        LocalDateTime slotStart = today.atTime(slot.start());
        LocalDateTime slotEnd = today.atTime(slot.end());
        List<ShiftAssignmentModel> overlapping = assignmentRepository.findPublishedAssignmentsOverlapping(
                user.getId(),
                slotStart.minusMinutes(30),
                slotEnd.plusMinutes(30),
                ShiftStatus.PUBLISHED);
        if (!overlapping.isEmpty()) {
            return Optional.of(overlapping.get(0));
        }

        if (allowOutsideHoursTestMode && !todayAssignments.isEmpty()) {
            return todayAssignments.stream()
                    .min(Comparator.comparingLong(assignment -> Math.abs(
                            Duration.between(assignment.getShift().getStartTime(), slotStart).toMinutes())));
        }
        return Optional.empty();
    }

    private boolean shiftMatchesSlot(ShiftModel shift, ShiftSlotDeriver.SlotTemplate slot, LocalDate day) {
        if (shift.getStartTime() == null || shift.getEndTime() == null) {
            return false;
        }
        if (!shift.getStartTime().toLocalDate().equals(day)) {
            return false;
        }
        LocalDateTime expectedStart = day.atTime(slot.start());
        LocalDateTime expectedEnd = day.atTime(slot.end());
        long startDiff = Math.abs(Duration.between(shift.getStartTime(), expectedStart).toMinutes());
        long endDiff = Math.abs(Duration.between(shift.getEndTime(), expectedEnd).toMinutes());
        return startDiff <= 2 && endDiff <= 2;
    }

    private void applySlotContext(ShiftSessionResponse response, UserModel user) {
        if (user.getBranchId() == null) {
            return;
        }
        branchRepository.findById(user.getBranchId()).ifPresent(branch -> {
            List<ShiftSlotDeriver.SlotTemplate> slots = ShiftSlotDeriver.derive(branch.getOperatingHours());
            LocalTime now = LocalTime.now();
            int index = ShiftSlotDeriver.indexForTime(slots, now);
            if (index < 0 && allowOutsideHoursTestMode) {
                index = ShiftSlotDeriver.nearestSlotIndex(slots, now);
                response.setOutsideOperatingHours(true);
            } else if (index < 0) {
                response.setOutsideOperatingHours(true);
            }
            if (index >= 0 && index < slots.size()) {
                ShiftSlotDeriver.SlotTemplate slot = slots.get(index);
                response.setCurrentSlotIndex(index);
                response.setCurrentSlotLabel("Shift " + (index + 1));
                response.setCurrentSlotStart(slot.start().toString());
                response.setCurrentSlotEnd(slot.end().toString());
            }
        });
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
        Optional<ShiftAssignmentModel> reusable = overlapping.stream()
                .filter(assignment -> sessionRepository
                        .findFirstByShiftIdAndEmployeeIdOrderByIdDesc(
                                assignment.getShift().getId(), user.getId())
                        .map(session -> session.getStatus() == ShiftSessionStatus.SCHEDULED
                                || session.getStatus().isActiveForCashier()
                                || session.getStatus() == ShiftSessionStatus.PENDING_APPROVAL
                                || session.getStatus() == ShiftSessionStatus.REJECTED)
                        .orElse(true))
                .findFirst();
        if (reusable.isPresent()) {
            return reusable.get();
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
                .orElseThrow(() -> new BusinessException(
                        allowOutsideHoursTestMode
                                ? "No published shift is assigned to you for the current time slot (Shift "
                                        + resolveSlotLabelForNow(user)
                                        + "). Ask your branch manager to publish and assign today's schedule."
                                : "No published shift is assigned to you for the current operating hours."));
    }

    private String resolveSlotLabelForNow(UserModel user) {
        if (user.getBranchId() == null) {
            return "?";
        }
        return branchRepository.findById(user.getBranchId())
                .map(branch -> {
                    List<ShiftSlotDeriver.SlotTemplate> slots = ShiftSlotDeriver.derive(branch.getOperatingHours());
                    LocalTime now = LocalTime.now();
                    int index = ShiftSlotDeriver.indexForTime(slots, now);
                    if (index < 0) {
                        index = ShiftSlotDeriver.nearestSlotIndex(slots, now);
                    }
                    return String.valueOf(index + 1);
                })
                .orElse("?");
    }

    private ShiftSessionModel getOrCreateScheduledSession(UserModel user, ShiftAssignmentModel assignment) {
        return resolveSessionForAssignment(user, assignment);
    }

    /**
     * Loads or creates the shift session for an assignment. When a terminal session
     * still falls inside the published shift window, it may be reset so cashiers can
     * reopen after auto-close or local clock changes during QA.
     */
    private ShiftSessionModel resolveSessionForAssignment(UserModel user, ShiftAssignmentModel assignment) {
        ShiftModel shift = assignment.getShift();
        ShiftSessionModel session = sessionRepository
                .findFirstByShiftIdAndEmployeeIdOrderByIdDesc(shift.getId(), user.getId())
                .orElseGet(() -> createNewScheduledSession(user, assignment, shift));
        if (maybeReopenTerminalSessionWithinShiftWindow(session, shift)) {
            session = sessionRepository.save(session);
        }
        syncShiftAssignmentId(session, assignment);
        return session;
    }

    /**
     * Validates that a cashier may open (start) a shift.
     */
    private ShiftSessionModel resolveSessionForStart(UserModel user, ShiftAssignmentModel assignment) {
        ShiftSessionModel session = resolveSessionForAssignment(user, assignment);
        ShiftSessionStatus status = session.getStatus();
        if (status == ShiftSessionStatus.OPEN || status == ShiftSessionStatus.SCHEDULED) {
            return session;
        }
        throw new BusinessException(blockedStartMessage(status));
    }

    /**
     * Re-schedule a terminal session when the shift slot has not ended yet. Covers
     * auto-close after the grace period and QA scenarios where the OS clock is moved
     * backward after a close recorded in the "future".
     */
    private boolean maybeReopenTerminalSessionWithinShiftWindow(
            ShiftSessionModel session, ShiftModel shift) {
        if (session == null || shift == null || shift.getEndTime() == null) {
            return false;
        }
        ShiftSessionStatus status = session.getStatus();
        if (status != ShiftSessionStatus.COMPLETED
                && status != ShiftSessionStatus.CLOSED
                && status != ShiftSessionStatus.APPROVED) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(shift.getEndTime())) {
            return false;
        }
        boolean autoClosed = isAutoClosed(session);
        boolean clockRewound =
                session.getClosedAt() != null && session.getClosedAt().isAfter(now);
        if (!autoClosed && !clockRewound && !allowOutsideHoursTestMode) {
            return false;
        }
        resetSessionForReopening(session, shift);
        return true;
    }

    private boolean isAutoClosed(ShiftSessionModel session) {
        return AUTO_CLOSE_NOTE.equals(session.getClosingNote())
                || AUTO_CLOSE_NOTE.equals(session.getHandoverRemark());
    }

    private void resetSessionForReopening(ShiftSessionModel session, ShiftModel shift) {
        session.setStatus(ShiftSessionStatus.SCHEDULED);
        session.setOpenedAt(null);
        session.setClosedAt(null);
        session.setOpeningConfirmed(false);
        session.setVerificationConfirmed(false);
        session.setHandoverConfirmed(false);
        session.setActualCash(null);
        session.setDifference(null);
        session.setCashSales(null);
        session.setExpectedCash(null);
        session.setTransactionCount(null);
        session.setRefundAmount(null);
        session.setClosingNote(null);
        session.setHandoverRemark(null);
        session.setHandoverToEmployeeId(null);
        session.setOpeningFundReceivedFrom(null);
        session.setOpeningFundMethod(null);
        session.setOpeningFundReceivedAt(null);
        session.setOpeningNote(null);
        session.setApprovedBy(null);
        session.setApprovedAt(null);
        session.setManagerNote(null);
        populateOpeningFund(session, shift);
    }

    private void assertCanBeginOpening(ShiftSessionModel session) {
        if (session.getStatus() == ShiftSessionStatus.SCHEDULED) {
            return;
        }
        if (session.getStatus() == ShiftSessionStatus.OPEN) {
            throw new BusinessException("This shift is already open.");
        }
        throw new BusinessException(blockedStartMessage(session.getStatus()));
    }

    private String blockedStartMessage(ShiftSessionStatus status) {
        return switch (status) {
            case PENDING_APPROVAL -> "This shift is waiting for branch manager cash approval. "
                    + "You cannot open it again until the manager approves or rejects the closing.";
            case REJECTED -> "Your shift closing was rejected by the branch manager. "
                    + "Open Shift Closing, recount the cash, and submit again.";
            case CLOSING, PENDING_HANDOVER -> "This shift is in closing. "
                    + "Finish shift closing before you can open it again.";
            case COMPLETED -> "This shift has already been completed. "
                    + "You cannot open the same shift again; wait for the next published shift from your branch manager.";
            case CLOSED -> "This shift session is already closed. "
                    + "Wait for the next published shift from your branch manager.";
            case APPROVED -> "This shift has already been finalized. "
                    + "Wait for the next published shift from your branch manager.";
            default -> "Shift cannot be started in its current state (" + status + ").";
        };
    }

    private ShiftSessionModel createNewScheduledSession(
            UserModel user, ShiftAssignmentModel assignment, ShiftModel shift) {
        ShiftSessionModel session = new ShiftSessionModel();
        session.setShiftId(shift.getId());
        session.setShiftAssignmentId(assignment.getId());
        session.setEmployeeId(user.getId());
        session.setRole(user.getRole());
        session.setBranchId(shift.getBranchId());
        session.setStatus(ShiftSessionStatus.SCHEDULED);
        populateOpeningFund(session, shift);
        return sessionRepository.save(session);
    }

    private void syncShiftAssignmentId(ShiftSessionModel session, ShiftAssignmentModel assignment) {
        if (session.getShiftAssignmentId() == null) {
            session.setShiftAssignmentId(assignment.getId());
            sessionRepository.save(session);
        }
    }

    private void populateOpeningFund(ShiftSessionModel session, ShiftModel shift) {
        if (session.getRole() != UserRole.CASHIER) {
            return;
        }
        if (isFirstPublishedShiftOfDay(shift)) {
            session.setOpeningFundAmount(STANDARD_OPENING_FUND);
        } else {
            findPreviousCashierSession(session).ifPresentOrElse(
                    previous -> {
                        if (previous.getActualCash() != null) {
                            session.setOpeningFundAmount(previous.getActualCash());
                        } else if (session.getOpeningFundAmount() == null) {
                            session.setOpeningFundAmount(
                                    shift.getOpeningCash() != null ? shift.getOpeningCash() : BigDecimal.ZERO);
                        }
                    },
                    () -> {
                        if (session.getOpeningFundAmount() == null) {
                            session.setOpeningFundAmount(
                                    shift.getOpeningCash() != null ? shift.getOpeningCash() : BigDecimal.ZERO);
                        }
                    });
        }
        if (session.getOpeningFundReceivedFrom() == null) {
            sessionRepository
                    .findFirstByBranchIdAndStatusInAndRoleOrderByClosedAtDesc(
                            session.getBranchId(), HANDOVER_FUND_STATUSES, UserRole.CASHIER)
                    .filter(previous -> !Objects.equals(previous.getEmployeeId(), session.getEmployeeId()))
                    .ifPresentOrElse(
                            previous -> session.setOpeningFundReceivedFrom(previous.getEmployeeId()),
                            () -> session.setOpeningFundReceivedFrom(shift.getCreatedBy()));
        }
        if (session.getOpeningFundMethod() == null) {
            session.setOpeningFundMethod(FundTransferMethod.CASH);
        }
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

    /**
     * Closes a cashier session that is still active after shift end + grace period.
     * Returns true when the session was auto-closed.
     */
    private boolean autoCloseIfOverdue(ShiftSessionModel session) {
        if (session == null || session.getRole() != UserRole.CASHIER) {
            return false;
        }
        if (!CASHIER_ACTIVE_STATUSES.contains(session.getStatus())) {
            return false;
        }
        ShiftModel shift = shiftRepository.findById(session.getShiftId()).orElse(null);
        if (shift == null || shift.getEndTime() == null) {
            return false;
        }
        LocalDateTime deadline = shift.getEndTime().plusMinutes(autoCloseGraceMinutes);
        if (!LocalDateTime.now().isAfter(deadline)) {
            return false;
        }
        return performAutoClose(session);
    }

    private boolean performAutoClose(ShiftSessionModel session) {
        if (session.getRole() != UserRole.CASHIER) {
            return false;
        }
        if (!CASHIER_ACTIVE_STATUSES.contains(session.getStatus())) {
            return false;
        }

        refreshCashierTotals(session);
        ensureHighValueItems(session);
        autoConfirmHighValueItems(session);

        BigDecimal expected =
                session.getExpectedCash() != null ? session.getExpectedCash() : BigDecimal.ZERO;
        session.setActualCash(expected);
        session.setDifference(BigDecimal.ZERO);
        session.setVerificationConfirmed(true);
        session.setHandoverConfirmed(true);
        session.setHandoverRemark(AUTO_CLOSE_NOTE);
        session.setClosingNote(AUTO_CLOSE_NOTE);
        session.setClosedAt(LocalDateTime.now());
        session.setStatus(ShiftSessionStatus.COMPLETED);
        sessionRepository.save(session);

        shiftRepository.findById(session.getShiftId()).ifPresent(shift -> {
            shift.setExpectedCash(session.getExpectedCash());
            shift.setActualCash(session.getActualCash());
            shift.setDifference(BigDecimal.ZERO);
            shiftRepository.save(shift);
        });

        assignmentRepository
                .findFirstByShiftIdAndStaffIdOrderByIdDesc(session.getShiftId(), session.getEmployeeId())
                .ifPresent(assignment -> {
                    if (assignment.getCheckOutAt() == null) {
                        assignment.setCheckOutAt(LocalDateTime.now());
                        assignmentRepository.save(assignment);
                    }
                });

        notifyBranchManagersOfAutoClose(session);
        return true;
    }

    /** Legacy or auto-closed sessions with no cash/product difference do not need BM review. */
    private void finalizeBalancedPendingSessions(Long branchId) {
        sessionRepository
                .findByBranchIdAndStatusOrderByClosedAtDesc(branchId, ShiftSessionStatus.PENDING_APPROVAL)
                .stream()
                .filter(session -> !requiresReconciliationApproval(session))
                .forEach(session -> {
                    session.setStatus(ShiftSessionStatus.COMPLETED);
                    sessionRepository.save(session);
                });
    }

    private boolean hasCashDifference(BigDecimal difference) {
        return difference != null && difference.compareTo(BigDecimal.ZERO) != 0;
    }

    private void autoConfirmHighValueItems(ShiftSessionModel session) {
        List<ShiftSessionHighValueItemModel> items =
                highValueItemRepository.findBySessionIdOrderByIdAsc(session.getId());
        for (ShiftSessionHighValueItemModel item : items) {
            int expected = item.getExpectedQty() != null ? item.getExpectedQty() : 0;
            if (item.getActualQty() == null) {
                item.setActualQty(expected);
            }
            item.setDifference(item.getActualQty() - expected);
        }
        if (!items.isEmpty()) {
            highValueItemRepository.saveAll(items);
        }
    }

    private void notifyBranchManagersOfAutoClose(ShiftSessionModel session) {
        List<UserModel> managers = userRepository.findActiveBranchManagers(session.getBranchId());
        if (managers.isEmpty()) {
            return;
        }

        UserModel cashier = userRepository.findById(session.getEmployeeId()).orElse(null);
        String cashierName = cashier != null ? formatName(cashier) : "Cashier #" + session.getEmployeeId();
        String branchName = branchRepository.findById(session.getBranchId())
                .map(branch -> branch.getName() != null ? branch.getName() : "Branch #" + session.getBranchId())
                .orElse("Branch #" + session.getBranchId());

        ShiftModel shift = shiftRepository.findById(session.getShiftId()).orElse(null);
        String shiftWindow = shift != null && shift.getStartTime() != null && shift.getEndTime() != null
                ? shift.getStartTime() + " – " + shift.getEndTime()
                : "N/A";

        String reviewUrl = clientUrl.replaceAll("/$", "")
                + "/branch-manager/cash-reconciliation/"
                + session.getId();
        String subject = "[ChainStore] Shift auto-closed — review required";
        String body = """
                Hello Branch Manager,

                A cashier shift was automatically closed because the cashier did not complete closing within %d minutes after the scheduled shift end time.

                Branch: %s
                Cashier: %s
                Shift window: %s
                Expected cash: %s VND
                System note: %s

                Please review and approve or reject this closing in Cash Reconciliation:
                %s

                — ChainStore System
                """.formatted(
                autoCloseGraceMinutes,
                branchName,
                cashierName,
                shiftWindow,
                session.getExpectedCash() != null ? session.getExpectedCash().toPlainString() : "0",
                AUTO_CLOSE_NOTE,
                reviewUrl);

        for (UserModel manager : managers) {
            if (manager.getEmail() == null || manager.getEmail().isBlank()) {
                continue;
            }
            emailService.sendPlainTextEmail(manager.getEmail(), subject, body);
        }
    }

    private void ensureHighValueItems(ShiftSessionModel session) {
        if (session.getRole() != UserRole.CASHIER) {
            return;
        }
        dedupeHighValueItemsForSession(session.getId());
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
        Map<Integer, BigDecimal> effectivePrices = productSalePriceService == null
                ? products.values().stream().collect(Collectors.toMap(
                        ProductModel::getId, ProductModel::getDefaultSalePrice))
                : productSalePriceService.effectivePrices(new ArrayList<>(products.values()));

        List<ShiftSessionHighValueItemModel> seeds = new ArrayList<>();
        Set<String> seenProductNames = new HashSet<>();
        for (BranchInventoryModel row : inventory) {
            ProductModel product = products.get(row.getProductId());
            BigDecimal price = product == null ? null : effectivePrices.get(product.getId());
            if (price == null) {
                continue;
            }
            if (!qualifiesForHighValueVerification(product, price)) {
                continue;
            }
            String dedupeKey = product.getName() != null
                    ? product.getName().trim().toLowerCase(Locale.ROOT)
                    : "id-" + product.getId();
            if (!seenProductNames.add(dedupeKey)) {
                continue;
            }
            ShiftSessionHighValueItemModel item = new ShiftSessionHighValueItemModel();
            item.setSessionId(session.getId());
            item.setProductId(product.getId());
            item.setExpectedQty(row.getCurrentStock() != null ? row.getCurrentStock() : 0);
            seeds.add(item);
        }
        seeds.sort(Comparator.comparing(
                i -> effectivePrices.get(i.getProductId()), Comparator.reverseOrder()));
        if (seeds.size() > HIGH_VALUE_MAX_ITEMS) {
            seeds = seeds.subList(0, HIGH_VALUE_MAX_ITEMS);
        }
        highValueItemRepository.saveAll(seeds);
    }

    /** Drop cached duplicate lines (same product or same display name) left from old seeds. */
    private void dedupeHighValueItemsForSession(Long sessionId) {
        List<ShiftSessionHighValueItemModel> existing =
                highValueItemRepository.findBySessionIdOrderByIdAsc(sessionId);
        if (existing.isEmpty()) {
            return;
        }
        Set<Integer> productIds = existing.stream()
                .map(ShiftSessionHighValueItemModel::getProductId)
                .collect(Collectors.toSet());
        Map<Integer, ProductModel> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity()));

        List<ShiftSessionHighValueItemModel> ranked = new ArrayList<>(existing);
        ranked.sort(Comparator
                .comparing((ShiftSessionHighValueItemModel row) ->
                        catalogPreferenceScore(products.get(row.getProductId())))
                .reversed()
                .thenComparing(ShiftSessionHighValueItemModel::getId));

        Set<Integer> seenProductIds = new HashSet<>();
        Set<String> seenNames = new HashSet<>();
        List<ShiftSessionHighValueItemModel> toDelete = new ArrayList<>();
        for (ShiftSessionHighValueItemModel row : ranked) {
            ProductModel product = products.get(row.getProductId());
            if (product == null) {
                toDelete.add(row);
                continue;
            }
            if (!seenProductIds.add(row.getProductId())) {
                toDelete.add(row);
                continue;
            }
            String nameKey = normalizeProductName(product.getName());
            if (!seenNames.add(nameKey)) {
                toDelete.add(row);
            }
        }
        if (!toDelete.isEmpty()) {
            highValueItemRepository.deleteAll(toDelete);
        }
    }

    private String normalizeProductName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /** Prefer SQL catalog (TOB/COS/CRD/ALC) over legacy BE seed codes. */
    private int catalogPreferenceScore(ProductModel product) {
        if (product == null || product.getCode() == null) {
            return 0;
        }
        String code = product.getCode();
        if (code.startsWith("TOB") || code.startsWith("COS")
                || code.startsWith("CRD") || code.startsWith("ALC")) {
            return 2;
        }
        if (code.startsWith("CVS-HV-")) {
            return 1;
        }
        return 0;
    }

    private boolean qualifiesForHighValueVerification(ProductModel product, BigDecimal price) {
        if (isHighRiskCategory(product.getCategory())) {
            return price.compareTo(HIGH_RISK_CATEGORY_PRICE_THRESHOLD) >= 0;
        }
        return price.compareTo(HIGH_VALUE_PRICE_THRESHOLD) >= 0;
    }

    private boolean isHighRiskCategory(CategoryModel category) {
        if (category == null || category.getName() == null) {
            return false;
        }
        String name = category.getName().toLowerCase(Locale.ROOT);
        return name.contains("thuốc lá")
                || name.contains("tobacco")
                || name.contains("mỹ phẩm")
                || name.contains("cosmetic")
                || name.contains("beauty")
                || name.contains("thẻ cào")
                || name.contains("thẻ dịch vụ")
                || name.contains("prepaid")
                || name.contains("service card")
                || name.contains("cồn giá trị cao")
                || name.contains("premium alcohol");
    }

    private void applyOpeningFundReceipt(
            ShiftSessionModel session, Long receivedFromEmployeeId, FundTransferMethod method) {
        List<ShiftHandoverCandidateResponse> sources = buildOpeningFundSources(session);
        if (receivedFromEmployeeId != null) {
            boolean allowed = sources.stream()
                    .anyMatch(source -> Objects.equals(source.getEmployeeId(), receivedFromEmployeeId));
            if (!allowed) {
                throw new BusinessException("Select a valid opening fund source for this branch.");
            }
            session.setOpeningFundReceivedFrom(receivedFromEmployeeId);
        }
        if (session.getOpeningFundReceivedFrom() == null) {
            throw new BusinessException("Opening fund source is required.");
        }
        session.setOpeningFundMethod(method != null ? method : FundTransferMethod.CASH);
    }

    private List<ShiftHandoverCandidateResponse> buildOpeningFundSources(ShiftSessionModel session) {
        Map<Long, ShiftHandoverCandidateResponse> candidates = new LinkedHashMap<>();
        sessionRepository
                .findFirstByBranchIdAndStatusInAndRoleOrderByClosedAtDesc(
                        session.getBranchId(), HANDOVER_FUND_STATUSES, UserRole.CASHIER)
                .filter(previous -> !Objects.equals(previous.getEmployeeId(), session.getEmployeeId()))
                .flatMap(previous -> userRepository.findById(previous.getEmployeeId()))
                .ifPresent(user -> candidates.put(
                        user.getId(), toHandoverCandidate(user, null, false)));
        userRepository.findActiveBranchManagers(session.getBranchId()).forEach(manager -> candidates.putIfAbsent(
                manager.getId(), toHandoverCandidate(manager, null, false)));
        if (session.getOpeningFundReceivedFrom() != null) {
            userRepository.findById(session.getOpeningFundReceivedFrom()).ifPresent(user -> candidates.putIfAbsent(
                    user.getId(), toHandoverCandidate(user, null, false)));
        }
        return new ArrayList<>(candidates.values());
    }

    private List<ShiftHandoverCandidateResponse> buildHandoverCandidates(ShiftSessionModel session) {
        ShiftModel current = shiftRepository.findById(session.getShiftId()).orElse(null);
        if (current == null || current.getStartTime() == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<ShiftModel> sameDay = shiftRepository
                .findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        session.getBranchId(),
                        current.getStartTime().toLocalDate().atStartOfDay(),
                        current.getStartTime().toLocalDate().plusDays(1).atStartOfDay());
        Map<Long, ShiftHandoverCandidateResponse> candidates = new LinkedHashMap<>();
        for (ShiftModel candidateShift : sameDay) {
            if (candidateShift.getStatus() != ShiftStatus.PUBLISHED
                    || candidateShift.getStartTime() == null
                    || candidateShift.getEndTime() == null) {
                continue;
            }
            boolean coversNow = !candidateShift.getStartTime().isAfter(now)
                    && candidateShift.getEndTime().isAfter(now);
            boolean currentShift = Objects.equals(candidateShift.getId(), current.getId());
            boolean followsCurrent = current.getEndTime() != null
                    && !candidateShift.getStartTime().isBefore(current.getEndTime());
            if (!currentShift && !coversNow && !followsCurrent) {
                continue;
            }
            for (ShiftAssignmentModel assignment : assignmentRepository.findByShiftId(candidateShift.getId())) {
                UserModel staff = assignment.getStaff();
                if (staff == null
                        || effectiveRole(assignment) != UserRole.CASHIER
                        || Objects.equals(staff.getId(), session.getEmployeeId())) {
                    continue;
                }
                ShiftHandoverCandidateResponse candidate =
                        toHandoverCandidate(staff, candidateShift, coversNow);
                ShiftHandoverCandidateResponse existing = candidates.get(staff.getId());
                if (existing == null
                        || (!Boolean.TRUE.equals(existing.getScheduledReplacement()) && coversNow)) {
                    candidates.put(staff.getId(), candidate);
                }
            }
        }
        userRepository.findActiveBranchManagers(session.getBranchId()).forEach(manager -> candidates.putIfAbsent(
                manager.getId(), toHandoverCandidate(manager, null, false)));
        return new ArrayList<>(candidates.values());
    }

    private ShiftHandoverCandidateResponse toHandoverCandidate(
            UserModel user, ShiftModel shift, boolean scheduledReplacement) {
        ShiftHandoverCandidateResponse response = new ShiftHandoverCandidateResponse();
        response.setEmployeeId(user.getId());
        response.setEmployeeName(formatName(user));
        response.setRole(user.getRole());
        response.setScheduledReplacement(scheduledReplacement);
        if (shift != null) {
            response.setShiftId(shift.getId());
            response.setShiftStart(shift.getStartTime());
            response.setShiftEnd(shift.getEndTime());
        }
        return response;
    }

    private ShiftHandoverCandidateResponse resolveSelectedHandoverTarget(
            ShiftSessionModel session, Long requestedEmployeeId) {
        List<ShiftHandoverCandidateResponse> candidates = buildHandoverCandidates(session);
        Long targetId = requestedEmployeeId != null
                ? requestedEmployeeId
                : session.getHandoverToEmployeeId();
        if (targetId == null) {
            targetId = candidates.stream()
                    .filter(candidate -> !isEarlyClose(session)
                            || Boolean.TRUE.equals(candidate.getScheduledReplacement()))
                    .map(ShiftHandoverCandidateResponse::getEmployeeId)
                    .findFirst()
                    .orElse(null);
        }
        Long selectedId = targetId;
        return candidates.stream()
                .filter(candidate -> Objects.equals(candidate.getEmployeeId(), selectedId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Select a valid cashier or branch manager for cash handover."));
    }

    private boolean isEarlyClose(ShiftSessionModel session) {
        return shiftRepository.findById(session.getShiftId())
                .map(ShiftModel::getEndTime)
                .filter(Objects::nonNull)
                .map(endTime -> LocalDateTime.now().isBefore(endTime))
                .orElse(false);
    }

    private void resolveHandoverTarget(ShiftSessionModel session) {
        if (session.getHandoverToEmployeeId() != null) {
            return;
        }
        List<ShiftHandoverCandidateResponse> candidates = buildHandoverCandidates(session);
        candidates.stream()
                .filter(candidate -> !isEarlyClose(session)
                        || Boolean.TRUE.equals(candidate.getScheduledReplacement()))
                .findFirst()
                .ifPresent(candidate -> session.setHandoverToEmployeeId(candidate.getEmployeeId()));
    }

    private UserRole effectiveRole(ShiftAssignmentModel assignment) {
        if (assignment.getAssignedRole() != null) {
            return assignment.getAssignedRole();
        }
        return assignment.getStaff().getRole();
    }

    private Optional<ShiftSessionModel> findPreviousCashierSession(ShiftSessionModel session) {
        return sessionRepository
                .findFirstByBranchIdAndStatusInAndRoleOrderByClosedAtDesc(
                        session.getBranchId(), HANDOVER_FUND_STATUSES, UserRole.CASHIER)
                .filter(previous -> !Objects.equals(previous.getEmployeeId(), session.getEmployeeId()))
                .filter(previous -> !Objects.equals(previous.getId(), session.getId()));
    }

    /**
     * Prior closed cashier session for product variance (may be same employee on a previous shift).
     */
    private Optional<ShiftSessionModel> findPriorClosedCashierSessionForVariance(ShiftSessionModel session) {
        LocalDateTime before = session.getOpenedAt() != null ? session.getOpenedAt() : LocalDateTime.now();
        return sessionRepository
                .findByBranchIdAndStatusInOrderByOpenedAtDesc(session.getBranchId(), HANDOVER_FUND_STATUSES)
                .stream()
                .filter(previous -> previous.getRole() == UserRole.CASHIER)
                .filter(previous -> !Objects.equals(previous.getId(), session.getId()))
                .filter(previous -> previous.getClosedAt() != null && previous.getClosedAt().isBefore(before))
                .max(Comparator.comparing(ShiftSessionModel::getClosedAt));
    }

    private List<PreviousShiftProductVarianceResponse> buildPreviousShiftProductVariance(
            ShiftSessionModel session,
            List<ShiftSessionHighValueItemModel> currentItems) {
        Optional<ShiftSessionModel> previousOpt = findPriorClosedCashierSessionForVariance(session);
        if (previousOpt.isEmpty()) {
            return List.of();
        }
        ShiftSessionModel previous = previousOpt.get();
        dedupeHighValueItemsForSession(previous.getId());
        Map<Integer, HighValueItemResponse> previousByProduct =
                mapHighValueItems(highValueItemRepository.findBySessionIdOrderByIdAsc(previous.getId()))
                        .stream()
                        .filter(item -> item.getProductId() != null)
                        .collect(Collectors.toMap(
                                HighValueItemResponse::getProductId,
                                Function.identity(),
                                (a, b) -> a));
        Map<Integer, HighValueItemResponse> currentByProduct =
                mapHighValueItems(currentItems).stream()
                        .filter(item -> item.getProductId() != null)
                        .collect(Collectors.toMap(
                                HighValueItemResponse::getProductId,
                                Function.identity(),
                                (a, b) -> a));

        Set<Integer> productIds = new HashSet<>();
        productIds.addAll(previousByProduct.keySet());
        productIds.addAll(currentByProduct.keySet());

        List<PreviousShiftProductVarianceResponse> rows = new ArrayList<>();
        for (Integer productId : productIds) {
            HighValueItemResponse current = currentByProduct.get(productId);
            HighValueItemResponse prevItem = previousByProduct.get(productId);
            PreviousShiftProductVarianceResponse row = new PreviousShiftProductVarianceResponse();
            row.setProductId(productId);
            row.setProductName(current != null ? current.getProductName() : prevItem.getProductName());
            row.setCategoryName(current != null ? current.getCategoryName() : prevItem.getCategoryName());
            Integer previousActual = prevItem != null ? prevItem.getActualQty() : null;
            Integer currentExpected = current != null ? current.getExpectedQty() : null;
            Integer currentActual = current != null ? current.getActualQty() : null;
            row.setPreviousActualQty(previousActual);
            row.setCurrentExpectedQty(currentExpected);
            row.setCurrentActualQty(currentActual);
            Integer compare = currentActual != null ? currentActual : currentExpected;
            if (previousActual != null && compare != null) {
                row.setVariance(compare - previousActual);
            }
            rows.add(row);
        }
        rows.sort(Comparator.comparing(
                PreviousShiftProductVarianceResponse::getProductName,
                Comparator.nullsLast(String::compareToIgnoreCase)));
        return rows;
    }

    private PreviousShiftHandoverReportResponse buildPreviousShiftReport(ShiftSessionModel previous) {
        PreviousShiftHandoverReportResponse report = new PreviousShiftHandoverReportResponse();
        report.setSessionId(previous.getId());
        report.setClosedAt(previous.getClosedAt());
        report.setExpectedCash(previous.getExpectedCash());
        report.setActualCash(previous.getActualCash());
        report.setDifference(previous.getDifference());
        report.setDifferenceStatus(resolveDifferenceStatus(previous.getDifference()));
        report.setHandoverRemark(previous.getHandoverRemark());
        userRepository.findById(previous.getEmployeeId()).ifPresent(u -> report.setEmployeeName(formatName(u)));
        shiftRepository.findById(previous.getShiftId())
                .ifPresent(shift -> report.setShiftNumber(shiftNumberForDay(shift)));
        if (previous.getId() != null) {
            dedupeHighValueItemsForSession(previous.getId());
            List<ShiftSessionHighValueItemModel> items =
                    highValueItemRepository.findBySessionIdOrderByIdAsc(previous.getId());
            report.setHighValueItems(mapHighValueItems(items));
        }
        report.setInventorySummary(buildInventorySummary(previous));
        return report;
    }

    private InventoryClosingSummaryResponse buildInventorySummary(ShiftSessionModel session) {
        InventoryClosingSummaryResponse summary = new InventoryClosingSummaryResponse();
        List<BranchInventoryModel> rows = branchInventoryRepository.findByBranchId(session.getBranchId());
        summary.setTotalSku(rows.size());
        summary.setLowStockSku((int) rows.stream()
                .filter(r -> r.getCurrentStock() != null && r.getCurrentStock() <= 5)
                .count());
        LocalDateTime from = session.getOpenedAt() != null ? session.getOpenedAt() : LocalDateTime.now().minusHours(4);
        LocalDateTime to = session.getClosedAt() != null ? session.getClosedAt() : LocalDateTime.now();
        int counts = (int) inventoryCountSessionRepository
                .findByBranchIdOrderByCreatedAtDesc(session.getBranchId())
                .stream()
                .filter(s -> s.getCreatedAt() != null
                        && !s.getCreatedAt().isBefore(from)
                        && !s.getCreatedAt().isAfter(to))
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
        response.setOpeningFundStatus(Boolean.TRUE.equals(session.getOpeningConfirmed())
                ? "OPENING_FUND_CONFIRMED"
                : "WAITING_FOR_OPENING_FUND");
        response.setVerificationConfirmed(session.getVerificationConfirmed());
        response.setHandoverConfirmed(session.getHandoverConfirmed());
        response.setOpeningNote(session.getOpeningNote());
        response.setClosingNote(session.getClosingNote());
        response.setOpeningFundAmount(session.getOpeningFundAmount());
        response.setOpeningFundReceivedFromEmployeeId(session.getOpeningFundReceivedFrom());
        response.setOpeningFundReceivedAt(session.getOpeningFundReceivedAt());
        response.setOpeningFundMethod(session.getOpeningFundMethod());
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

        if (session.getStatus() == ShiftSessionStatus.SCHEDULED) {
            response.setOpeningFundSources(buildOpeningFundSources(session));
            if (session.getRole() == UserRole.CASHIER) {
                findPreviousCashierSession(session)
                        .ifPresent(previous -> response.setPreviousShiftReport(buildPreviousShiftReport(previous)));
            }
        }
        if (session.getStatus() == ShiftSessionStatus.OPEN
                || session.getStatus() == ShiftSessionStatus.CLOSING
                || session.getStatus() == ShiftSessionStatus.PENDING_HANDOVER) {
            response.setEarlyClose(isEarlyClose(session));
            response.setHandoverCandidates(buildHandoverCandidates(session));
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
            dedupeHighValueItemsForSession(session.getId());
            List<ShiftSessionHighValueItemModel> items =
                    highValueItemRepository.findBySessionIdOrderByIdAsc(session.getId());
            response.setHighValueItems(mapHighValueItems(items));
            applyProductDiscrepancyFlags(response, items);
            if (session.getOpeningNote() != null && session.getOpeningNote().startsWith(JOINED_SHIFT_NOTE_PREFIX)) {
                response.setJoinedExistingShift(true);
                response.setShiftOpenedByName(session.getOpeningNote().substring(JOINED_SHIFT_NOTE_PREFIX.length()));
            }
            if (session.getStatus() != ShiftSessionStatus.SCHEDULED) {
                response.setPreviousShiftProductVariance(
                        buildPreviousShiftProductVariance(session, items));
            }
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
