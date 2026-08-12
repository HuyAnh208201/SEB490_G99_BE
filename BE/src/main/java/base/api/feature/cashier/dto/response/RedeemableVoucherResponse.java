package base.api.feature.cashier.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One voucher type a customer can buy with points. Deliberately thinner than the admin
 * view: the counter only needs what is on offer and what it costs.
 */
@Getter
@Setter
public class RedeemableVoucherResponse {

    private Long voucherCatalogId;
    private String name;
    /** PERCENT or FIXED. */
    private String discountType;
    private BigDecimal discountValue;
    private Integer pointsRequired;
}
