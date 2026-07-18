package base.api.feature.inventory.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BranchInventoryItemResponse {

    private Long inventoryId;
    private Long branchId;
    private String branchName;
    private Integer productId;
    private String productCode;
    private String productName;
    private String unit;
    private Integer quantity;
}
