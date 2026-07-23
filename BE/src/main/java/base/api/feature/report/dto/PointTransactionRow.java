package base.api.feature.report.dto;

import java.time.LocalDateTime;

/** Dòng lịch sử tích điểm thô (projection từ point_transactions), chưa resolve tên. */
public record PointTransactionRow(
        Long id,
        Long customerId,
        Long orderId,
        Long points,
        String type,
        LocalDateTime createdAt) {
}
