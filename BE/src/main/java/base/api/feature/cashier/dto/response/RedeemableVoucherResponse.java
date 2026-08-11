package base.api.feature.cashier.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Một loại voucher khách có thể đổi bằng điểm. Cố ý gọn hơn màn quản lý: quầy chỉ
 * cần biết đổi được gì và hết bao nhiêu điểm.
 */
@Getter
@Setter
public class RedeemableVoucherResponse {

    private Long voucherCatalogId;
    private String name;
    /** PERCENT hoặc FIXED. */
    private String discountType;
    private BigDecimal discountValue;
    private Integer pointsRequired;
}
