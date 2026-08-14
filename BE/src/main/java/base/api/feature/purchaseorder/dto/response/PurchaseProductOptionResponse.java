package base.api.feature.purchaseorder.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Kết quả tìm sản phẩm để thêm tay vào đơn đặt hàng.
 */
@Getter
@Setter
public class PurchaseProductOptionResponse {
    private Integer productId;
    private String productCode;
    private String productName;
    private String categoryName;
    private String unit;
    private String importUnit;
    private Integer conversionQty;
    private String topPackagingLabel;
    private Integer currentQty;
    private BigDecimal referencePrice;
}
