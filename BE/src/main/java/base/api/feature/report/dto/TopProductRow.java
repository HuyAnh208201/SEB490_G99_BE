package base.api.feature.report.dto;

import java.math.BigDecimal;

public record TopProductRow(
        Integer productId,
        String productName,
        long qtySold,
        BigDecimal revenue,
        BigDecimal cogs,
        BigDecimal profit
) {
}
