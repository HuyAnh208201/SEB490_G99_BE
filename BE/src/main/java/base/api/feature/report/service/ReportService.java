package base.api.feature.report.service;

import base.api.feature.report.dto.CashDiscrepancyResponse;
import base.api.feature.report.dto.InvoiceRow;
import base.api.feature.report.dto.PointTransactionResponse;
import base.api.feature.report.dto.ReportSummaryResponse;
import base.api.feature.report.dto.RevenueReportResponse;
import base.api.feature.report.dto.RevenueRow;
import base.api.feature.report.dto.TopProductRow;
import base.api.feature.report.dto.TrendPoint;
import base.api.shared.dto.PageRequestDTO;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo kinh doanh. Mỗi phương thức tự áp phạm vi (scope) theo vai trò người gọi:
 * ADMIN/DIRECTOR xem toàn hệ thống (branchId optional), BRANCH_MANAGER bị ép về chi
 * nhánh của mình (branchId truyền lên bị bỏ qua).
 */
public interface ReportService {

    RevenueReportResponse getRevenue(String groupBy, LocalDate from, LocalDate to, Long branchId);

    Page<RevenueRow> getRevenuePage(String groupBy, LocalDate from, LocalDate to, Long branchId, PageRequestDTO pageRequest);

    ReportSummaryResponse getSummary(LocalDate from, LocalDate to, Long branchId, Long shiftId);

    List<TrendPoint> getTrend(LocalDate from, LocalDate to, Long branchId, Long shiftId);

    List<TopProductRow> getTopProducts(LocalDate from, LocalDate to, Long branchId, Long shiftId, int limit);

    List<InvoiceRow> getInvoices(LocalDate from, LocalDate to, Long branchId);

    Page<InvoiceRow> getInvoicePage(LocalDate from, LocalDate to, Long branchId, PageRequestDTO pageRequest);

    List<CashDiscrepancyResponse> getCashDiscrepancies(LocalDate from, LocalDate to, Long branchId);

    Page<CashDiscrepancyResponse> getCashDiscrepancyPage(LocalDate from, LocalDate to, Long branchId, PageRequestDTO pageRequest);

    List<PointTransactionResponse> getPointTransactions(LocalDate from, LocalDate to, Long branchId);

    Page<PointTransactionResponse> getPointTransactionPage(LocalDate from, LocalDate to, Long branchId, PageRequestDTO pageRequest);
}
