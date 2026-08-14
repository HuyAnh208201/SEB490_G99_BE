package base.api.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "products")
public class ProductModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 255)
    private String code;

    @Column(unique = true, length = 255)
    private String barcode;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CategoryModel category;

    @Column(length = 255)
    private String unit;

    /** Retail/selling unit code (English). */
    @Column(name = "import_unit", length = 64)
    private String importUnit;

    /** How many retail units are in one import unit (e.g. 24 cans per case). */
    @Column(name = "units_per_import_unit")
    private Integer unitsPerImportUnit;

    @Column(nullable = false, length = 16)
    private String scope = "GLOBAL";

    @Column(name = "branch_id")
    private Long branchId;

    /** Primary supplier used by the warehouse receiving workflow. */
    @Column(name = "supplier_id")
    private Integer supplierId;

    @Column(name = "reference_import_price", precision = 15, scale = 2)
    private BigDecimal referenceImportPrice;

    @Column(name = "default_sale_price", precision = 15, scale = 2)
    private BigDecimal defaultSalePrice;

    @Column(nullable = false)
    private Boolean refundable = true;

    @Column(nullable = false, length = 255)
    private String status = "active";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
