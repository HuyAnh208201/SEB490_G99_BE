package base.api.feature.report.dto;

import java.math.BigDecimal;

/**
 * Một dòng doanh thu đã gom nhóm.
 *
 * {@code id} = shiftId / cashierId / branchId tuỳ groupBy. {@code name} chỉ có giá trị
 * khi gom theo nhân viên (tên thu ngân), null với các kiểu gom khác.
 */
public record RevenueRow(
        Long id,
        String name,
        long orderCount,
        BigDecimal revenue,
        BigDecimal cogs,
        BigDecimal profit
) {
}
