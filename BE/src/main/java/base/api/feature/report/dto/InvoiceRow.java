package base.api.feature.report.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một dòng lịch sử hoá đơn (projection trực tiếp từ orders).
 *
 * Bao gồm mọi trạng thái (COMPLETED/REFUNDED...) để người xem thấy cả đơn đã hoàn.
 */
public record InvoiceRow(
        Long id,
        String invoiceCode,
        Long branchId,
        Long cashierId,
        Long customerId,
        BigDecimal total,
        String status,
        LocalDateTime createdAt) {
}
