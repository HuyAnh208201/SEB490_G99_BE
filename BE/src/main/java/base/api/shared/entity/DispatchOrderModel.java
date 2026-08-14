package base.api.shared.entity;

import base.api.shared.enums.DispatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Lô vận chuyển — một dispatch order gắn với một yêu cầu nhập hàng đã duyệt.
 */
@Getter
@Setter
@Entity
@Table(name = "dispatch_orders")
public class DispatchOrderModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DispatchStatus status = DispatchStatus.PREPARING;

    @Column(name = "delivery_area", length = 100)
    private String deliveryArea;

    @Column(name = "route", length = 100)
    private String route;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "recipient_id")
    private Long recipientId;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "shipper_name", length = 150)
    private String shipperName;

    @Column(name = "shipper_phone", length = 20)
    private String shipperPhone;
}
