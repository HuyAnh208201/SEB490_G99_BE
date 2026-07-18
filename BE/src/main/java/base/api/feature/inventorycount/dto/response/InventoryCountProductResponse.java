package base.api.feature.inventorycount.dto.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Một dòng sản phẩm trong phiếu kiểm kê (system qty lấy từ tồn kho chi nhánh).
 */
@Getter
@Setter
public class InventoryCountProductResponse {
    private Integer productId;
    private String productCode;
    private String productName;
    private String unit;
    private String category;
    private Integer systemQty;
}
