package base.api.feature.posorder.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Kết quả tra mã giảm giá tại quầy. */
@Getter
@Setter
public class VoucherResponse {

    private Long voucherId;
    private String code;
    private String name;
    /** PERCENT hoặc FIXED. */
    private String discountType;
    private BigDecimal discountValue;
    private LocalDateTime expiresAt;
}
