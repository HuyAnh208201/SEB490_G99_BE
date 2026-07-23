package base.api.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Loại voucher: giảm theo phần trăm hay số tiền cố định. */
@Getter
@Setter
@Entity
@Table(name = "voucher_catalog")
public class VoucherCatalogModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    /** PERCENT hoặc FIXED. */
    @Column(name = "discount_type", nullable = false, length = 32)
    private String discountType;

    /** PERCENT thì là số phần trăm, FIXED thì là số tiền VNĐ. */
    @Column(name = "discount_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "points_required", nullable = false)
    private Integer pointsRequired = 0;

    @Column(nullable = false, length = 32)
    private String status = "active";
}
