package base.api.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One packaging/UoM level for a product (e.g. Bottle -> Case of 24).
 * Levels are ordered by {@code sortOrder}: level 1 is always the base/retail
 * unit (conversionQty = 1, isBase = true); the last level is the TOP/import
 * unit used for purchase requests (isPurchaseDefault = true). Stock ledgers
 * (branch_inventory, warehouse_inventory) always store quantities in the
 * BASE unit; multiply by conversionQty to go from this packaging to base.
 */
@Getter
@Setter
@Entity
@Table(name = "product_packagings")
public class ProductPackagingModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    /** Short machine code for this packaging level, unique per product (e.g. "case24"). */
    @Column(name = "code", length = 64)
    private String code;

    /** Display name as stored (may be legacy/local wording). */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** English display label shown on FE (falls back to {@code name} when null). */
    @Column(name = "label_en", length = 128)
    private String labelEn;

    /** How many BASE units this one packaging unit contains. Base level = 1. */
    @Column(name = "conversion_qty", nullable = false)
    private Integer conversionQty;

    @Column(name = "barcode", length = 255)
    private String barcode;

    /** True for the level-1 base/retail packaging (conversionQty = 1). */
    @Column(name = "is_base")
    private Boolean isBase = false;

    /** True for the TOP/import level used by purchase requests. */
    @Column(name = "is_purchase_default")
    private Boolean isPurchaseDefault = false;

    /** Ascending order from base (0) to top level. */
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    public String displayLabel() {
        if (labelEn != null && !labelEn.isBlank()) {
            return labelEn;
        }
        return name;
    }
}
