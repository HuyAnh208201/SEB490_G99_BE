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
import java.time.LocalDateTime;

/** Một hoá đơn bán tại quầy. */
@Getter
@Setter
@Entity
@Table(name = "orders")
public class OrderModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    /** Ca đang mở lúc bán, null nếu bán ngoài ca. */
    @Column(name = "shift_id")
    private Long shiftId;

    @Column(name = "cashier_id", nullable = false)
    private Long cashierId;

    /** Trỏ tới users.id (role CUSTOMER), null nếu khách vãng lai không cung cấp SĐT. */
    @Column(name = "customer_id")
    private Long customerId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "points_redeemed", nullable = false)
    private Long pointsRedeemed = 0L;

    @Column(name = "points_earned", nullable = false)
    private Long pointsEarned = 0L;

    @Column(name = "invoice_code", length = 64)
    private String invoiceCode;

    @Column(nullable = false, length = 32)
    private String status = "COMPLETED";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
