package base.api.feature.report.dto;

import java.math.BigDecimal;

/** Raw KPI aggregate from JPQL constructor expression. Null aggregates when no rows. */
public record OrderSummaryAgg(Long transactionCount, BigDecimal totalRevenue) {
}
