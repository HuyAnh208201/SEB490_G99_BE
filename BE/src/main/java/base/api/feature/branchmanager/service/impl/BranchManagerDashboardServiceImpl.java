package base.api.feature.branchmanager.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.branchmanager.dto.response.BranchManagerDashboardResponse;
import base.api.feature.branchmanager.service.IBranchManagerDashboardService;
import base.api.feature.posorder.repository.OrderRefundRepository;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.feature.report.dto.ReportSummaryResponse;
import base.api.feature.report.service.ReportService;
import base.api.feature.shift.repository.ShiftRepository;
import base.api.feature.shiftsession.repository.ShiftSessionRepository;
import base.api.shared.entity.BranchInventoryModel;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.PurchaseRequestStatus;
import base.api.shared.enums.ShiftSessionStatus;
import base.api.shared.enums.ShiftStatus;
import base.api.shared.exception.BadRequestException;
import base.api.shared.security.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class BranchManagerDashboardServiceImpl implements IBranchManagerDashboardService {

    private static final List<PurchaseRequestStatus> OPEN_IMPORT_STATUSES = List.of(
            PurchaseRequestStatus.PENDING,
            PurchaseRequestStatus.APPROVED,
            PurchaseRequestStatus.AWAITING_STOCK,
            PurchaseRequestStatus.DISPATCHING,
            PurchaseRequestStatus.IN_TRANSIT
    );

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private IBranchRepository branchRepository;

    @Autowired
    private ReportService reportService;

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private OrderRefundRepository orderRefundRepository;

    @Autowired
    private ShiftSessionRepository shiftSessionRepository;

    @Autowired
    private BranchInventoryRepository branchInventoryRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private IUserRepository userRepository;

    @Override
    public BranchManagerDashboardResponse getDashboard(LocalDate from, LocalDate to) {
        UserModel me = currentUserProvider.getCurrentUserOrThrow();
        Long branchId = me.getBranchId();
        if (branchId == null) {
            throw new BadRequestException("Branch not assigned.");
        }
        BranchModel branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new BadRequestException("Branch not found."));

        LocalDate rangeTo = to != null ? to : LocalDate.now();
        LocalDate rangeFrom = from != null ? from : rangeTo.withDayOfMonth(1);
        LocalDate today = LocalDate.now();

        ReportSummaryResponse period = reportService.getSummary(rangeFrom, rangeTo, null, null);
        ReportSummaryResponse todaySummary = reportService.getSummary(today, today, null, null);
        BigDecimal momPercent = computeMom(rangeFrom, rangeTo, period.totalRevenue());

        long lowStock = branchInventoryRepository.findByBranchId(branchId).stream()
                .filter(this::isLowStock)
                .count();

        return new BranchManagerDashboardResponse(
                branchId,
                branch.getName(),
                rangeFrom,
                rangeTo,
                todaySummary.totalRevenue(),
                todaySummary.totalProfit(),
                todaySummary.transactionCount(),
                period.totalRevenue(),
                period.totalCogs(),
                period.totalProfit(),
                period.profitMarginPercent(),
                period.transactionCount(),
                period.avgTransactionValue(),
                momPercent,
                reportService.getTrend(rangeFrom, rangeTo, null, null),
                reportService.getTopProducts(rangeFrom, rangeTo, null, null, 5),
                purchaseRequestRepository.countByBranchIdAndStatusIn(branchId, OPEN_IMPORT_STATUSES),
                orderRefundRepository.countByBranchIdAndStatus(branchId, "PENDING"),
                shiftSessionRepository.countPendingReconciliationWithDifference(
                        branchId, ShiftSessionStatus.PENDING_APPROVAL),
                lowStock,
                shiftRepository.countByBranchIdAndStatus(branchId, ShiftStatus.PUBLISHED),
                userRepository.countStaffByBranchId(branchId),
                java.time.LocalDateTime.now()
        );
    }

    private boolean isLowStock(BranchInventoryModel row) {
        int qty = row.getCurrentStock() == null ? 0 : row.getCurrentStock();
        int reorder = row.getReorderPoint() == null ? 0 : row.getReorderPoint();
        if (reorder > 0) {
            return qty <= reorder;
        }
        return qty <= 5;
    }

    private BigDecimal computeMom(LocalDate from, LocalDate to, BigDecimal currentRevenue) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days < 1) {
            return null;
        }
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);
        ReportSummaryResponse prev = reportService.getSummary(prevFrom, prevTo, null, null);
        BigDecimal previous = prev.totalRevenue() != null ? prev.totalRevenue() : BigDecimal.ZERO;
        BigDecimal current = currentRevenue != null ? currentRevenue : BigDecimal.ZERO;
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }
}
