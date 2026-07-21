package base.api.feature.purchaserequest.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PurchaseRequestDetailResponse {
    private Long id;
    private Integer productId;
    private String productCode;
    private String productName;
    private String categoryName;
    private String unit;
    /** Requested / approved quantities are expressed in TOP packaging units (see topPackagingLabel). */
    private Integer requestedQty;
    private Integer approvedQuantity;
    private Integer supplierId;
    /** English label of the TOP packaging level this request line is quoted in, e.g. "Case of 24". */
    private String topPackagingLabel;
    /** How many base units one TOP packaging unit contains. */
    private Integer topPackagingConversionQty;
    /** Tồn kho KHO TỔNG hiện có cho sản phẩm này (để kho tổng biết còn/hết hàng khi duyệt). */
    private Integer warehouseStock;
}
