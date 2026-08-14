package base.api.feature.branchmanager.dto.response;

import base.api.feature.report.dto.TopProductRow;
import base.api.feature.report.dto.TrendPoint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Branch ops cockpit — period results + action queues. */
public record BranchManagerDashboardResponse(
        Long branchId,
        String branchName,
        LocalDate from,
        LocalDate to,
        BigDecimal todayRevenue,
        BigDecimal todayProfit,
        long todayTransactions,
        BigDecimal periodRevenue,
        BigDecimal periodCogs,
        BigDecimal periodProfit,
        BigDecimal profitMarginPercent,
        long periodTransactions,
        BigDecimal avgTransactionValue,
        BigDecimal momPercent,
        List<TrendPoint> trend,
        List<TopProductRow> topProducts,
        long pendingImports,
        long pendingRefunds,
        long pendingReconciliations,
        long lowStockSkus,
        long publishedShifts,
        long staffCount,
        java.time.LocalDateTime generatedAt
) {
}
