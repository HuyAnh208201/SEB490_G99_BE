package base.api.feature.purchaserequest.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductSearchResponse {
    private Integer productId;
    private String productCode;
    private String barcode;
    private String productName;
    private String categoryName;
    private String unit;
}
