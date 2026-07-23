package base.api.feature.report.dto;

import java.math.BigDecimal;

/**
 * Dòng tổng hợp doanh thu thô từ JPQL (constructor expression).
 *
 * {@code groupId} là shiftId / cashierId / branchId tuỳ theo cách gom nhóm; có thể
 * null (vd đơn bán ngoài ca thì shiftId null).
 */
public record RevenueAggRow(Long groupId, Long orderCount, BigDecimal revenue) {
}
