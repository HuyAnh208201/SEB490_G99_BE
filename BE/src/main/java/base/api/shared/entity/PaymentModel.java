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

@Getter
@Setter
@Entity
@Table(name = "payments")
public class PaymentModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** CASH hoặc PAYOS. */
    @Column(nullable = false, length = 32)
    private String method;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "cash_received", precision = 15, scale = 2)
    private BigDecimal cashReceived;

    @Column(name = "change_amount", precision = 15, scale = 2)
    private BigDecimal changeAmount;

    @Column(name = "transaction_ref", length = 255)
    private String transactionRef;

    @Column(nullable = false, length = 32)
    private String status = "SUCCESS";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
