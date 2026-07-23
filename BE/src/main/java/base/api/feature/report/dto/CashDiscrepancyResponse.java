package base.api.feature.report.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Một dòng lịch sử chênh lệch tiền ca (đã resolve tên nhân viên + người duyệt). */
public record CashDiscrepancyResponse(
        Long sessionId,
        Long shiftId,
        String employeeName,
        BigDecimal expectedCash,
        BigDecimal actualCash,
        BigDecimal difference,
        String reviewedByName,
        String reviewNote,
        LocalDateTime closedAt) {
}
