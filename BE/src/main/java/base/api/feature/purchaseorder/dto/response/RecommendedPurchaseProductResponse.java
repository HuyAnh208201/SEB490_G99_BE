package base.api.feature.purchaseorder.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Sản phẩm KHO TỔNG đang thiếu (gợi ý đặt nhà cung cấp).
 * requiredQty = nhu cầu từ các yêu cầu AWAITING_STOCK hoặc reorder point.
 * suggestedQty = max(requiredQty - currentQty, 0).
 */
@Getter
@Setter
public class RecommendedPurchaseProductResponse {
    private Integer productId;
    private String productCode;
    private String productName;
    private String categoryName;
    private String unit;
    private Integer currentQty;
    private Integer requiredQty;
    private Integer suggestedQty;
    private BigDecimal referencePrice;
}
