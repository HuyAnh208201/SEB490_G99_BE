package base.api.feature.director.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Executive portfolio — branches, promos, exceptions (not a revenue-dashboard clone). */
public record DirectorDashboardResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal totalRevenue,
        BigDecimal totalCogs,
        BigDecimal totalProfit,
        BigDecimal profitMarginPercent,
        long transactionCount,
        BigDecimal avgTransactionValue,
        BigDecimal momPercent,
        BranchHighlight bestBranch,
        BranchHighlight weakestBranch,
        BigDecimal projectedRevenue7d,
        long activePromotions,
        long draftPromotions,
        long suspendedPromotions,
        List<PromoSummary> activeCampaigns,
        List<BranchPortfolioRow> branchPortfolio,
        long cashDiscrepancyCount,
        long pendingImportRequests,
        long exceptionCount
) {
    public record BranchHighlight(Long id, String name, BigDecimal revenue, BigDecimal profit) {
    }

    public record BranchPortfolioRow(
            Long branchId,
            String branchName,
            BigDecimal revenue,
            BigDecimal profit,
            long orderCount,
            BigDecimal shareOfChainPercent
    ) {
    }

    public record PromoSummary(
            Long id,
            String name,
            String status,
            String scope,
            LocalDateTime startAt,
            LocalDateTime endAt,
            int branchCount
    ) {
    }
}
