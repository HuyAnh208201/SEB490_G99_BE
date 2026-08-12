package base.api.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Một mã giảm giá cụ thể cashier gõ tại quầy. */
@Getter
@Setter
@Entity
@Table(name = "vouchers")
public class VoucherModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "voucher_catalog_id")
    private Long voucherCatalogId;

    /** Voucher phát riêng cho một khách; null nghĩa là ai dùng cũng được. */
    @Column(name = "customer_id")
    private Long customerId;

    /** active, used (applied to an order) or revoked (withdrawn by an admin). */
    @Column(nullable = false, length = 32)
    private String status = "active";

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
