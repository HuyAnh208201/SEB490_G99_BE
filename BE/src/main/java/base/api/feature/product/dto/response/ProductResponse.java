package base.api.feature.product.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class ProductResponse {

    private Integer id;
    private String code;
    private String barcode;
    private String name;
    private String description;
    private String imageUrl;
    private Integer categoryId;
    private String categoryName;
    private String unit;
    private String importUnit;
    private Integer unitsPerImportUnit;
    private Integer supplierId;
    /** English label of the TOP packaging level, e.g. "Case of 24" (source: product_packagings). */
    private String topPackagingLabel;
    /** How many base units one TOP packaging unit contains. */
    private Integer topPackagingConversionQty;
    private String scope;
    private Long branchId;
    private String branchName;
    private Integer branchStock;
    private Integer branchReorderPoint;
    private Integer warehouseStock;
    private Integer warehouseReorderPoint;
    private Boolean lowStock;
    private BigDecimal referenceImportPrice;
    private BigDecimal defaultSalePrice;
    private BigDecimal scheduledSalePrice;
    private java.time.LocalDate scheduledSalePriceEffectiveDate;
    private Boolean refundable;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
