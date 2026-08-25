package base.api.feature.purchaserequest.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductSearchResponse {
    private Integer productId;
    private String productCode;
    private String barcode;
    private String productName;
    private String categoryName;
    private Integer categoryId;
    private String unit;
    /** Reference import / unit cost from catalog (VND per base unit). */
    private BigDecimal unitCost;
    /** Alias of unitCost for clients that key off referenceImportPrice. */
    private BigDecimal referenceImportPrice;
    /** English label of the TOP packaging level, e.g. "Case of 24". */
    private String topPackagingLabel;
    /** How many base units one TOP packaging unit contains. */
    private Integer topPackagingConversionQty;
    /** Branch stock in base retail units (BM branch). */
    private Integer currentStock;
    /** Resolved branch reorder point (DB or category default). */
    private Integer reorderPoint;
    private Boolean lowStock;
}
