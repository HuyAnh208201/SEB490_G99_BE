package base.api.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "goods_receipts")
public class GoodsReceiptModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_request_id")
    private Long purchaseRequestId;

    @Column(name = "dispatch_order_id")
    private Long dispatchOrderId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "stock_staff_id", nullable = false)
    private Long stockStaffId;

    @Column(name = "status", length = 255)
    private String status = "completed";

    @CreationTimestamp
    @Column(name = "received_at", updatable = false)
    private LocalDateTime receivedAt;
}
