package base.api.shared.entity;

import base.api.shared.enums.PurchaseOrderStatus;
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
import java.time.LocalDate;

/**
 * Đơn đặt hàng nhà cung cấp (purchase order) để bổ sung tồn kho KHO TỔNG.
 */
@Getter
@Setter
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrderModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id")
    private Integer supplierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PurchaseOrderStatus status = PurchaseOrderStatus.ORDERED;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "supplier_delivery_date")
    private LocalDate supplierDeliveryDate;

    @Column(name = "delivered_by_name", length = 255)
    private String deliveredByName;

    @Column(name = "delivered_by_phone", length = 32)
    private String deliveredByPhone;

    @Column(name = "supplier_document_number", length = 100)
    private String supplierDocumentNumber;

    @Column(name = "received_by")
    private Long receivedBy;

    @Column(name = "received_by_name", length = 255)
    private String receivedByName;

    @Column(name = "received_by_phone", length = 32)
    private String receivedByPhone;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;
}
