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
}
