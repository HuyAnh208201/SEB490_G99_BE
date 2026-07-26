package base.api.feature.report.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.report.dto.CashDiscrepancyResponse;
import base.api.feature.report.dto.CashDiscrepancyRow;
import base.api.feature.report.dto.InvoiceRow;
import base.api.feature.report.dto.PointTransactionResponse;
import base.api.feature.report.dto.PointTransactionRow;
import base.api.feature.report.dto.RevenueAggRow;
import base.api.feature.report.dto.RevenueReportResponse;
import base.api.feature.report.dto.RevenueRow;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.feature.report.repository.ReportOrderRepository;
import base.api.feature.report.repository.ReportShiftSessionRepository;
import base.api.feature.report.service.ReportService;
import base.api.shared.entity.UserModel;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.enums.ShiftSessionStatus;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BusinessException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.security.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {

    /** Trần số dòng cho các báo cáo dạng danh sách — đủ để tra cứu, không kéo cả bảng. */
    private static final int LIST_LIMIT = 200;

    private static final String GROUP_BY_SHIFT = "shift";
    private static final String GROUP_BY_EMPLOYEE = "employee";
    private static final String GROUP_BY_BRANCH = "branch";

    @Autowired
    private ReportOrderRepository reportOrderRepository;

    @Autowired
    private ReportShiftSessionRepository reportShiftSessionRepository;

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    // =========================================================================
    // Doanh thu (theo ca / nhân viên / chi nhánh)
    // =========================================================================

    @Override
    public RevenueReportResponse getRevenue(String groupBy, LocalDate from, LocalDate to, Long branchId) {
        Long scopedBranchId = resolveBranchScope(branchId);
        LocalDateTime fromAt = startOf(from);
        LocalDateTime toAt = endExclusive(to);
        String mode = normalizeGroupBy(groupBy);

        List<RevenueAggRow> rows = switch (mode) {
            case GROUP_BY_EMPLOYEE -> reportOrderRepository.revenueByEmployee(scopedBranchId, fromAt, toAt);
            case GROUP_BY_BRANCH -> reportOrderRepository.revenueByBranch(scopedBranchId, fromAt, toAt);
            default -> reportOrderRepository.revenueByShift(scopedBranchId, fromAt, toAt);
        };

        // Chỉ gom theo nhân viên mới cần tên; ca/chi nhánh chỉ trả id.
        Map<Long, String> nameById = GROUP_BY_EMPLOYEE.equals(mode)
                ? resolveUserNames(rows.stream().map(RevenueAggRow::groupId).toList())
                : Map.of();

        List<RevenueRow> result = rows.stream()
                .map(row -> new RevenueRow(
                        row.groupId(),
                        GROUP_BY_EMPLOYEE.equals(mode) ? nameById.get(row.groupId()) : null,
                        row.orderCount() == null ? 0L : row.orderCount(),
                        row.revenue()))
                .toList();

        return new RevenueReportResponse(mode, result);
    }

    @Override
    public Page<RevenueRow> getRevenuePage(
            String groupBy,
            LocalDate from,
            LocalDate to,
            Long branchId,
            PageRequestDTO pageRequest
    ) {
        List<RevenueRow> rows = getRevenue(groupBy, from, to, branchId).rows();
        String search = pageRequest.normalizedSearch();
        if (search != null) {
            String normalized = search.toLowerCase(Locale.ROOT);
            rows = rows.stream()
                    .filter(row -> (row.name() != null && row.name().toLowerCase(Locale.ROOT).contains(normalized))
                            || String.valueOf(row.id()).contains(normalized))
                    .toList();
        }
        Pageable pageable = pageRequest.toPageable();
        int start = Math.min((int) pageable.getOffset(), rows.size());
        int end = Math.min(start + pageable.getPageSize(), rows.size());
        return new PageImpl<>(rows.subList(start, end), pageable, rows.size());
    }

    // =========================================================================
    // Lịch sử hoá đơn
    // =========================================================================

    @Override
    public List<InvoiceRow> getInvoices(LocalDate from, LocalDate to, Long branchId) {
        Long scopedBranchId = resolveBranchScope(branchId);
        return reportOrderRepository.findInvoices(scopedBranchId, startOf(from), endExclusive(to), limit());
    }

    @Override
    public Page<InvoiceRow> getInvoicePage(
            LocalDate from,
            LocalDate to,
            Long branchId,
            PageRequestDTO pageRequest
    ) {
        Long scopedBranchId = resolveBranchScope(branchId);
        return reportOrderRepository.findInvoicePage(
                scopedBranchId,
                startOf(from),
                endExclusive(to),
                searchPattern(pageRequest),
                pageRequest.toPageable());
    }

    // =========================================================================
    // Lịch sử chênh lệch tiền ca
    // =========================================================================

    @Override
    public List<CashDiscrepancyResponse> getCashDiscrepancies(LocalDate from, LocalDate to, Long branchId) {
        Long scopedBranchId = resolveBranchScope(branchId);
        List<CashDiscrepancyRow> rows = reportShiftSessionRepository.findDiscrepancies(
                UserRole.CASHIER,
                List.of(ShiftSessionStatus.COMPLETED, ShiftSessionStatus.APPROVED),
                scopedBranchId, startOf(from), endExclusive(to), limit());

        List<Long> userIds = rows.stream()
                .flatMap(row -> java.util.stream.Stream.of(row.employeeId(), row.reviewedBy()))
                .toList();
        Map<Long, String> nameById = resolveUserNames(userIds);

        return rows.stream()
                .map(row -> new CashDiscrepancyResponse(
                        row.sessionId(),
                        row.shiftId(),
                        nameById.get(row.employeeId()),
                        row.expectedCash(),
                        row.actualCash(),
                        row.difference(),
                        row.reviewedBy() == null ? null : nameById.get(row.reviewedBy()),
                        row.reviewNote(),
                        row.closedAt()))
                .toList();
    }

    @Override
    public Page<CashDiscrepancyResponse> getCashDiscrepancyPage(
            LocalDate from,
            LocalDate to,
            Long branchId,
            PageRequestDTO pageRequest
    ) {
        Long scopedBranchId = resolveBranchScope(branchId);
        Page<CashDiscrepancyRow> rows = reportShiftSessionRepository.findDiscrepancyPage(
                UserRole.CASHIER,
                List.of(ShiftSessionStatus.COMPLETED, ShiftSessionStatus.APPROVED),
                scopedBranchId,
                startOf(from),
                endExclusive(to),
                searchPattern(pageRequest),
                pageRequest.toPageable());
        List<Long> userIds = rows.getContent().stream()
                .flatMap(row -> java.util.stream.Stream.of(row.employeeId(), row.reviewedBy()))
                .toList();
        Map<Long, String> nameById = resolveUserNames(userIds);
        List<CashDiscrepancyResponse> content = rows.getContent().stream()
                .map(row -> new CashDiscrepancyResponse(
                        row.sessionId(),
                        row.shiftId(),
                        nameById.get(row.employeeId()),
                        row.expectedCash(),
                        row.actualCash(),
                        row.difference(),
                        row.reviewedBy() == null ? null : nameById.get(row.reviewedBy()),
                        row.reviewNote(),
                        row.closedAt()))
                .toList();
        return new PageImpl<>(content, rows.getPageable(), rows.getTotalElements());
    }

    // =========================================================================
    // Lịch sử tích điểm
    // =========================================================================

    @Override
    public List<PointTransactionResponse> getPointTransactions(LocalDate from, LocalDate to, Long branchId) {
        Long scopedBranchId = resolveBranchScope(branchId);
        List<PointTransactionRow> rows = pointTransactionRepository.findHistory(
                scopedBranchId, startOf(from), endExclusive(to), limit());

        Map<Long, String> nameById = resolveUserNames(
                rows.stream().map(PointTransactionRow::customerId).toList());

        return rows.stream()
                .map(row -> new PointTransactionResponse(
                        row.id(),
                        row.customerId(),
                        nameById.get(row.customerId()),
                        row.orderId(),
                        row.points(),
                        row.type(),
                        row.createdAt()))
                .toList();
    }

    @Override
    public Page<PointTransactionResponse> getPointTransactionPage(
            LocalDate from,
            LocalDate to,
            Long branchId,
            PageRequestDTO pageRequest
    ) {
        Long scopedBranchId = resolveBranchScope(branchId);
        Page<PointTransactionRow> rows = pointTransactionRepository.findHistoryPage(
                scopedBranchId,
                startOf(from),
                endExclusive(to),
                searchPattern(pageRequest),
                pageRequest.toPageable());
        Map<Long, String> nameById = resolveUserNames(
                rows.getContent().stream().map(PointTransactionRow::customerId).toList());
        List<PointTransactionResponse> content = rows.getContent().stream()
                .map(row -> new PointTransactionResponse(
                        row.id(),
                        row.customerId(),
                        nameById.get(row.customerId()),
                        row.orderId(),
                        row.points(),
                        row.type(),
                        row.createdAt()))
                .toList();
        return new PageImpl<>(content, rows.getPageable(), rows.getTotalElements());
    }

    // =========================================================================
    // Scope theo vai trò — RANH GIỚI BẢO MẬT
    // =========================================================================

    /**
     * BRANCH_MANAGER chỉ được xem chi nhánh của mình: ép branchId về branch của BM và
     * BỎ QUA branchId client gửi (chống BM dò dữ liệu chi nhánh khác). ADMIN/DIRECTOR
     * dùng branchId truyền lên (null = toàn hệ thống). Vai trò khác bị chặn (dù đã có
     * @PreAuthorize REPORTS_VIEW, vẫn kiểm lại tại tầng service cho chắc).
     */
    private Long resolveBranchScope(Long requestedBranchId) {
        UserRole role = currentUserProvider.getCurrentUserRole();
        if (role == UserRole.BRANCH_MANAGER) {
            UserModel manager = currentUserProvider.getCurrentUserOrThrow();
            if (manager.getBranchId() == null) {
                throw new BusinessException("Branch manager is not assigned to a branch.");
            }
            return manager.getBranchId();
        }
        if (role == UserRole.ADMIN || role == UserRole.DIRECTOR) {
            return requestedBranchId;
        }
        throw new ForbiddenException("You do not have access to business reports.");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String normalizeGroupBy(String groupBy) {
        if (groupBy == null || groupBy.isBlank()) {
            return GROUP_BY_SHIFT;
        }
        String value = groupBy.trim().toLowerCase();
        return switch (value) {
            case GROUP_BY_SHIFT, GROUP_BY_EMPLOYEE, GROUP_BY_BRANCH -> value;
            default -> throw new BusinessException(
                    "Unsupported groupBy. Use one of: shift, employee, branch.");
        };
    }

    private Map<Long, String> resolveUserNames(List<Long> ids) {
        Set<Long> distinct = ids.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(distinct).stream()
                .collect(Collectors.toMap(UserModel::getId, this::displayName, (first, ignored) -> first));
    }

    private String displayName(UserModel user) {
        String fullName = user.getFullName();
        if (fullName != null && !fullName.isBlank()) {
            return fullName.trim();
        }
        return user.getEmail();
    }

    private LocalDateTime startOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    /** Chặn trên theo ngày là bao trùm cả ngày 'to' nên cộng 1 ngày rồi so sánh '<'. */
    private LocalDateTime endExclusive(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay();
    }

    private Pageable limit() {
        return PageRequest.of(0, LIST_LIMIT);
    }

    private String searchPattern(PageRequestDTO pageRequest) {
        String search = pageRequest.normalizedSearch();
        return search == null ? null : "%" + search.toLowerCase(Locale.ROOT) + "%";
    }
}
