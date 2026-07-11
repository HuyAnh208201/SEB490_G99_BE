package base.api.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Liên kết giữa một lô vận chuyển và các yêu cầu nhập hàng được gom vào lô đó.
 */
@Getter
@Setter
@Entity
@Table(name = "dispatch_order_requests", uniqueConstraints = {
        @UniqueConstraint(name = "uq_dispatch_request", columnNames = {"dispatch_order_id", "purchase_request_id"})
})
public class DispatchOrderRequestModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dispatch_order_id", nullable = false)
    private Long dispatchOrderId;

    @Column(name = "purchase_request_id", nullable = false)
    private Long purchaseRequestId;
}
