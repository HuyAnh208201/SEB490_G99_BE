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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Phiên kiểm kê hàng hóa tại chi nhánh (inventory count).
 */
@Getter
@Setter
@Entity
@Table(name = "inventory_count_sessions")
public class InventoryCountSessionModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "count_date", nullable = false)
    private LocalDate countDate;

    @Column(name = "counted_by", nullable = false)
    private Long countedBy;

    /** PENDING_APPROVAL / APPROVED / REJECTED */
    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING_APPROVAL";

    @Column(name = "total_products")
    private Integer totalProducts = 0;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
