package base.api.feature.inventory.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WarehouseInventoryItemResponse {

    private Long inventoryId;
    private Integer productId;
    private String productCode;
    private String productName;
    private String unit;
    private Integer quantity;
    private Integer reorderPoint;
    private boolean lowStock;
}
