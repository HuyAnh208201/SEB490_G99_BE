package base.api.feature.purchaseorder.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Sản phẩm KHO TỔNG đang thiếu (gợi ý đặt nhà cung cấp).
 * Quantities for ordering (requiredQty, suggestedQty) are in TOP packaging units;
 * currentQtyBase / requiredQtyBase / suggestedQtyBase reflect warehouse ledger (BASE units).
 */
@Getter
@Setter
public class RecommendedPurchaseProductResponse {
    private Integer productId;
    private String productCode;
    private String productName;
    private String categoryName;
    /** TOP packaging label for PO line quantity (e.g. Case of 24). */
    private String unit;
    private String topPackagingLabel;
    /** Warehouse stock in BASE units. */
    private Integer currentQty;
    private Integer currentQtyBase;
    /** Required / suggested order quantity in TOP units. */
    private Integer requiredQty;
    private Integer requiredQtyBase;
    private Integer suggestedQty;
    private Integer suggestedQtyBase;
    private BigDecimal referencePrice;
}
