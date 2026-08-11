package base.api.feature.cashier.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Mã vừa sinh ra cho khách, kèm số điểm còn lại để cashier đọc lại cho khách. */
@Getter
@Setter
public class RedeemVoucherResponse {

    private Long voucherId;
    private String code;
    private String name;
    /** PERCENT hoặc FIXED. */
    private String discountType;
    private BigDecimal discountValue;
    private LocalDateTime expiresAt;
    private long pointsSpent;
    private long pointsRemaining;
}
