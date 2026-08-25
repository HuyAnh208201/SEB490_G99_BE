package base.api.feature.posorder.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ApplicablePromotionResponse {

    private Long id;
    private String name;
    private String type;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount;
    private boolean eligible;
    /** Present when {@code eligible} is false. */
    private String reason;
    /** Computed discount when eligible; otherwise null. */
    private BigDecimal discountAmount;
}
