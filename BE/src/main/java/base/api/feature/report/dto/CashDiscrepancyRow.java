package base.api.feature.report.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Dòng chênh lệch tiền ca thô (projection từ shift_sessions), chưa resolve tên. */
public record CashDiscrepancyRow(
        Long sessionId,
        Long shiftId,
        Long employeeId,
        BigDecimal expectedCash,
        BigDecimal actualCash,
        BigDecimal difference,
        Long reviewedBy,
        String reviewNote,
        LocalDateTime closedAt) {
}
