package base.api.feature.report.dto;

import java.math.BigDecimal;

public record TopProductAggRow(
        Integer productId,
        String productName,
        Long qtySold,
        BigDecimal revenue,
        BigDecimal cogs
) {
}
