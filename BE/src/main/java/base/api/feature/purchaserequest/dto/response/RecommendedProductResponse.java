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
    private Integer soldLast30Days;
    private String priorityReason;
    /** Suggested quantity expressed in TOP packaging units (what BM enters on the request). */
    private Integer suggestedQty;
    /** English label of the TOP packaging level, e.g. "Case of 24". */
    private String topPackagingLabel;
    /** How many base units one TOP packaging unit contains. */
    private Integer topPackagingConversionQty;
}
