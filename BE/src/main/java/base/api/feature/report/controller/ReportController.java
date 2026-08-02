package base.api.feature.report.controller;

import base.api.feature.report.dto.CashDiscrepancyResponse;
import base.api.feature.report.dto.InvoiceRow;
import base.api.feature.report.dto.PointTransactionResponse;
import base.api.feature.report.dto.ReportSummaryResponse;
import base.api.feature.report.dto.RevenueReportResponse;
import base.api.feature.report.dto.RevenueRow;
import base.api.feature.report.dto.TopProductRow;
import base.api.feature.report.dto.TrendPoint;
import base.api.feature.report.service.ReportService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.dto.PageResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo kinh doanh cho ADMIN/DIRECTOR (toàn hệ thống) và BRANCH_MANAGER (chỉ chi
 * nhánh của mình). Phạm vi dữ liệu do tầng service quyết theo vai trò — BM gửi branchId
 * chi nhánh khác cũng bị bỏ qua.
 */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "Báo cáo doanh thu, hoá đơn, chênh lệch tiền ca, tích điểm")
public class ReportController extends BaseAPIController {

    @Autowired
    private ReportService reportService;

    @Operation(
            summary = "Báo cáo doanh thu",
            description = "Gom nhóm theo ca (shift), nhân viên (employee) hoặc chi nhánh (branch). "
                    + "Chỉ tính đơn COMPLETED (loại đơn đã hoàn)."
    )
    @PreAuthorize("@permissionChecker.has('REPORTS_VIEW')")
    @GetMapping("/revenue")
    public ResponseEntity<TFUResponse<RevenueReportResponse>> revenue(
            @RequestParam(required = false, defaultValue = "shift") String groupBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long branchId) {

        return success(reportService.getRevenue(groupBy, from, to, branchId));
    }

    @Operation(summary = "Paginated revenue report")
    @PreAuthorize("@permissionChecker.has('REPORTS_VIEW')")
    @GetMapping("/revenue/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<RevenueRow>>> revenuePage(
            PageRequestDTO pageRequest,
            @RequestParam(required = false, defaultValue = "shift") String groupBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long branchId) {
        return successPage(reportService.getRevenuePage(groupBy, from, to, branchId, pageRequest));
    }

    @Operation(summary = "Revenue dashboard KPIs")
    @PreAuthorize("@permissionChecker.has('REPORTS_VIEW')")
    @GetMapping("/summary")
    public ResponseEntity<TFUResponse<ReportSummaryResponse>> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long shiftId) {
        return success(reportService.getSummary(from, to, branchId, shiftId));
    }

    @Operation(summary = "Daily revenue trend")
    @PreAuthorize("@permissionChecker.has('REPORTS_VIEW')")
    @GetMapping("/trend")
    public ResponseEntity<TFUResponse<List<TrendPoint>>> trend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long shiftId) {
        return success(reportService.getTrend(from, to, branchId, shiftId));
    }

    @Operation(summary = "Top-selling products by revenue")
    @PreAuthorize("@permissionChecker.has('REPORTS_VIEW')")
    @GetMapping("/top-products")
    public ResponseEntity<TFUResponse<List<TopProductRow>>> topProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long shiftId,
            @RequestParam(required = false, defaultValue = "5") int limit) {
        return success(reportService.getTopProducts(from, to, branchId, shiftId, limit));
    }

    @Operation(
            summary = "Lịch sử hoá đơn",
            description = "Danh sách hoá đơn mới nhất trước, lọc theo khoảng ngày và chi nhánh."
    )
    @PreAuthorize("@permissionChecker.has('REPORTS_VIEW')")
    @GetMapping("/invoices")
    public ResponseEntity<TFUResponse<List<InvoiceRow>>> invoices(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long branchId) {

        return success(reportService.getInvoices(from, to, branchId));
    }

    @Operation(summary = "Paginated invoice history")
    @PreAuthorize("@permissionChecker.has('REPORTS_VIEW')")
    @GetMapping("/invoices/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<InvoiceRow>>> invoicePage(
            PageRequestDTO pageRequest,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long branchId) {
        return successPage(reportService.getInvoicePage(from, to, branchId, pageRequest));
    }

    @Operation(
            summary = "Lịch sử chênh lệch tiền ca",
            description = "Các ca thu ngân đã được duyệt kèm số tiền dự kiến/thực tế và chênh lệch."
    )
    @PreAuthorize("@permissionChecker.has('REPORTS_VIEW')")
    @GetMapping("/cash-discrepancies")
    public ResponseEntity<TFUResponse<List<CashDiscrepancyResponse>>> cashDiscrepancies(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long branchId) {

        return success(reportService.getCashDiscrepancies(from, to, branchId));
    }

    @Operation(summary = "Paginated cash discrepancy history")
    @PreAuthorize("@permissionChecker.has('REPORTS_VIEW')")
    @GetMapping("/cash-discrepancies/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<CashDiscrepancyResponse>>> cashDiscrepancyPage(
            PageRequestDTO pageRequest,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long branchId) {
        return successPage(reportService.getCashDiscrepancyPage(from, to, branchId, pageRequest));
    }

    @Operation(
            summary = "Lịch sử tích điểm",
            description = "Các giao dịch cộng/trừ điểm của khách. Khi lọc theo chi nhánh, giao dịch "
                    + "tích điểm rời (không gắn đơn) không thuộc chi nhánh nào nên bị loại."
    )
    @PreAuthorize("@permissionChecker.has('REPORTS_VIEW')")
    @GetMapping("/point-transactions")
    public ResponseEntity<TFUResponse<List<PointTransactionResponse>>> pointTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long branchId) {

        return success(reportService.getPointTransactions(from, to, branchId));
    }

    @Operation(summary = "Paginated loyalty point history")
    @PreAuthorize("@permissionChecker.has('REPORTS_VIEW')")
    @GetMapping("/point-transactions/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<PointTransactionResponse>>> pointTransactionPage(
            PageRequestDTO pageRequest,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long branchId) {
        return successPage(reportService.getPointTransactionPage(from, to, branchId, pageRequest));
    }
}
