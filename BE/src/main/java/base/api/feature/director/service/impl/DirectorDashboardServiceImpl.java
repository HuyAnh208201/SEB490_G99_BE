package base.api.feature.director.service.impl;

import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.director.dto.response.DirectorDashboardResponse;
import base.api.feature.director.dto.response.DirectorDashboardResponse.BranchHighlight;
import base.api.feature.director.dto.response.DirectorDashboardResponse.BranchPortfolioRow;
import base.api.feature.director.dto.response.DirectorDashboardResponse.PromoSummary;
import base.api.feature.director.service.IDirectorDashboardService;
import base.api.feature.promotion.dto.response.CampaignSummaryResponse;
import base.api.feature.promotion.repository.CampaignRepository;
import base.api.feature.promotion.service.ICampaignService;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.feature.report.dto.ReportSummaryResponse;
import base.api.feature.report.dto.RevenueReportResponse;
import base.api.feature.report.dto.RevenueRow;
import base.api.feature.report.dto.TrendPoint;
import base.api.feature.report.service.ReportService;
import base.api.shared.entity.BranchModel;
import base.api.shared.enums.CampaignStatus;
import base.api.shared.enums.PurchaseRequestStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DirectorDashboardServiceImpl implements IDirectorDashboardService {

    @Autowired
    private ReportService reportService;

    @Autowired
    private IBranchRepository branchRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private ICampaignService campaignService;

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Override
    public DirectorDashboardResponse getDashboard(LocalDate from, LocalDate to) {
        LocalDate rangeTo = to != null ? to : LocalDate.now();
        LocalDate rangeFrom = from != null ? from : rangeTo.withDayOfMonth(1);

        ReportSummaryResponse summary = reportService.getSummary(rangeFrom, rangeTo, null, null);
        BigDecimal momPercent = computeMom(rangeFrom, rangeTo, summary.totalRevenue());

        RevenueReportResponse byBranch = reportService.getRevenue("branch", rangeFrom, rangeTo, null);
        Map<Long, RevenueRow> revenueByBranchId = new HashMap<>();
        for (RevenueRow row : byBranch.rows()) {
            if (row.id() != null) {
                revenueByBranchId.put(row.id(), row);
            }
        }

        BigDecimal chainRevenue = summary.totalRevenue() != null ? summary.totalRevenue() : BigDecimal.ZERO;
        List<BranchPortfolioRow> portfolio = new ArrayList<>();
        for (BranchModel branch : branchRepository.findAll()) {
            RevenueRow row = revenueByBranchId.get(branch.getId());
            BigDecimal revenue = row != null && row.revenue() != null ? row.revenue() : BigDecimal.ZERO;
            BigDecimal profit = row != null && row.profit() != null ? row.profit() : BigDecimal.ZERO;
            long orders = row != null ? row.orderCount() : 0L;
            BigDecimal share = BigDecimal.ZERO;
            if (chainRevenue.compareTo(BigDecimal.ZERO) > 0) {
                share = revenue.multiply(BigDecimal.valueOf(100))
                        .divide(chainRevenue, 1, RoundingMode.HALF_UP);
            }
            portfolio.add(new BranchPortfolioRow(
                    branch.getId(),
                    branch.getName(),
                    revenue,
                    profit,
                    orders,
                    share
            ));
        }
        portfolio.sort(Comparator.comparing(BranchPortfolioRow::revenue).reversed());

        BranchHighlight best = null;
        BranchHighlight weakest = null;
        if (!portfolio.isEmpty()) {
            BranchPortfolioRow top = portfolio.get(0);
            BranchPortfolioRow bottom = portfolio.get(portfolio.size() - 1);
            if (top.revenue().compareTo(BigDecimal.ZERO) > 0) {
                best = new BranchHighlight(top.branchId(), top.branchName(), top.revenue(), top.profit());
            }
            weakest = new BranchHighlight(bottom.branchId(), bottom.branchName(), bottom.revenue(), bottom.profit());
        }

        BigDecimal projected = project7Day(rangeTo);

        LocalDateTime now = LocalDateTime.now();
        // Same window as customer mobile: ACTIVE and currently within [startAt, endAt].
        long activePromos = campaignRepository.countLiveByStatus(CampaignStatus.ACTIVE, now);
        long draftPromos = campaignRepository.countByStatus(CampaignStatus.DRAFT);
        long suspendedPromos = campaignRepository.countByStatus(CampaignStatus.SUSPENDED);

        List<PromoSummary> activeCampaigns = campaignService.getAllCampaigns().stream()
                .filter(c -> CampaignStatus.ACTIVE.name().equalsIgnoreCase(c.getStatus()))
                .filter(c -> c.getStartAt() != null && !c.getStartAt().isAfter(now))
                .filter(c -> c.getEndAt() != null && !c.getEndAt().isBefore(now))
                .sorted(Comparator.comparing(
                        CampaignSummaryResponse::getEndAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(8)
                .map(c -> new PromoSummary(
                        c.getId(),
                        c.getName(),
                        c.getStatus(),
                        c.getScope(),
                        c.getStartAt(),
                        c.getEndAt(),
                        c.getBranchIds() == null ? 0 : c.getBranchIds().size()
                ))
                .toList();

        long cashGaps = reportService.getCashDiscrepancies(rangeFrom, rangeTo, null).size();
        long pendingImports = purchaseRequestRepository.countByStatusIn(List.of(
                PurchaseRequestStatus.PENDING,
                PurchaseRequestStatus.AWAITING_STOCK
        ));
        long exceptions = cashGaps + pendingImports;

        return new DirectorDashboardResponse(
                rangeFrom,
                rangeTo,
                summary.totalRevenue(),
                summary.totalCogs(),
                summary.totalProfit(),
                summary.profitMarginPercent(),
                summary.transactionCount(),
                summary.avgTransactionValue(),
                momPercent,
                best,
                weakest,
                projected,
                activePromos,
                draftPromos,
                suspendedPromos,
                activeCampaigns,
                portfolio,
                cashGaps,
                pendingImports,
                exceptions
        );
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

    private BigDecimal project7Day(LocalDate asOf) {
        LocalDate from = asOf.minusDays(13);
        List<TrendPoint> trend = reportService.getTrend(from, asOf, null, null);
        if (trend == null || trend.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = trend.stream()
                .map(t -> t.revenue() != null ? t.revenue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dailyAvg = sum.divide(BigDecimal.valueOf(trend.size()), 2, RoundingMode.HALF_UP);
        return dailyAvg.multiply(BigDecimal.valueOf(7)).setScale(0, RoundingMode.HALF_UP);
    }
}
