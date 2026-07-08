package base.api.feature.purchaserequest.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecommendedProductResponse {
    private Integer productId;
    private String productCode;
    private String productName;
    private String categoryName;
    private String unit;
    private Integer currentStock;
    private Integer reorderPoint;
    private Integer suggestedQty;
}
