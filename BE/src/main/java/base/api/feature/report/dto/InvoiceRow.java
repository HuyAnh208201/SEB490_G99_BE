package base.api.feature.report.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một dòng lịch sử hoá đơn (projection trực tiếp từ orders).
 *
 * Invoice history for completed / cancelled / refunded orders only
 * (excludes unpaid drafts such as PENDING_PAYMENT).
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
