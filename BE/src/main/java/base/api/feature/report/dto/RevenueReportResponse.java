package base.api.feature.report.dto;

import java.util.List;

/** Kết quả báo cáo doanh thu: echo lại cách gom nhóm + danh sách dòng. */
public record RevenueReportResponse(String groupBy, List<RevenueRow> rows) {
}
