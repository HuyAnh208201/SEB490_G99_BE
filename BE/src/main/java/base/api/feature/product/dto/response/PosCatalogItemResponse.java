package base.api.feature.product.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Minimal product row for POS counter — keep payload small and fast. */
@Getter
@Setter
public class PosCatalogItemResponse {
    private Integer id;
    private String code;
    private String barcode;
    private String name;
    private String unit;
    private Integer categoryId;
    private String categoryName;
    private BigDecimal defaultSalePrice;
    private Boolean refundable;
    private Integer branchStock;
    private String imageUrl;
}
