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
    private String categoryName;
    private String unit;
    private Integer quantity;
    private Integer reorderPoint;
    private Boolean lowStock;
    /** English label of the TOP packaging level, e.g. "Case of 24" — used for purchase requests. */
    private String topPackagingLabel;
    /** How many base units one TOP packaging unit contains. */
    private Integer topPackagingConversionQty;
}
