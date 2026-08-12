package base.api.feature.voucher.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class VoucherCatalogResponse {

    private Long id;
    private String name;
    /** PERCENT or FIXED. */
    private String discountType;
    private BigDecimal discountValue;
    private Integer pointsRequired;
    private String status;
    /** Whether any issued code references this type — the web app hides Delete when true. */
    private boolean inUse;
}
