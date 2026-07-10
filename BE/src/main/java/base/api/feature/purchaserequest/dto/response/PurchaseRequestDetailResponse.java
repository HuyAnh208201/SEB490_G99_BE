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
    private Integer requestedQty;
    private Integer approvedQuantity;
    private Integer supplierId;
    /** Tồn kho KHO TỔNG hiện có cho sản phẩm này (để kho tổng biết còn/hết hàng khi duyệt). */
    private Integer warehouseStock;
}
