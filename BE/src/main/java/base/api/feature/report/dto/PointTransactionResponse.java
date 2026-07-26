package base.api.feature.report.dto;

import java.time.LocalDateTime;

/** Một dòng lịch sử tích điểm (đã resolve tên khách hàng). */
public record PointTransactionResponse(
        Long id,
        Long customerId,
        String customerName,
        Long orderId,
        Long points,
        String type,
        LocalDateTime createdAt) {
}
