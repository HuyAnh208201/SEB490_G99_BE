package base.api.feature.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TrendPoint(LocalDate date, BigDecimal revenue, long orderCount, BigDecimal cogs, BigDecimal profit) {
}
