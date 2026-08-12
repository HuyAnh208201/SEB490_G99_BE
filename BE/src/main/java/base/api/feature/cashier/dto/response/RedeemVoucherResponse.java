package base.api.feature.cashier.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** The freshly issued code plus the remaining points, so the cashier can read both out. */
@Getter
@Setter
public class RedeemVoucherResponse {

    private Long voucherId;
    private String code;
    private String name;
    /** PERCENT or FIXED. */
    private String discountType;
    private BigDecimal discountValue;
    private LocalDateTime expiresAt;
    private long pointsSpent;
    private long pointsRemaining;
}
