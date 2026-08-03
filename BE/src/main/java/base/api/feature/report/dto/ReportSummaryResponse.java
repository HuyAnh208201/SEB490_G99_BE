package base.api.feature.report.dto;

import java.math.BigDecimal;

/**
 * KPI strip for Revenue Dashboard / Branch Performance.
 * {@code topBranch} is null when there is no completed revenue in range.
 */
public record ReportSummaryResponse(
        BigDecimal totalRevenue,
        long transactionCount,
        BigDecimal avgTransactionValue,
        TopBranchSummary topBranch
) {
    public record TopBranchSummary(Long id, String name, BigDecimal revenue) {
    }
}
