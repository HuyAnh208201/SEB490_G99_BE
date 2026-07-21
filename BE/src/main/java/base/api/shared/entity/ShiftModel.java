package base.api.shared.entity;

import base.api.shared.enums.ShiftStatus;
import base.api.shared.enums.ShiftStatusConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "shifts")
public class ShiftModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "opening_cash")
    private BigDecimal openingCash;

    @Column(name = "expected_cash")
    private BigDecimal expectedCash;

    @Column(name = "actual_cash")
    private BigDecimal actualCash;

    private BigDecimal difference;

    @Convert(converter = ShiftStatusConverter.class)
    @Column(nullable = false, length = 50)
    private ShiftStatus status = ShiftStatus.DRAFT;

    @Column(name = "approved_by")
    private Long approvedBy;

    /** ID của staff đã đóng ca (Cashier hoặc Inventory Staff). */
    @Column(name = "closed_by")
    private Long closedBy;

    /** Ghi chú của staff khi đóng ca. */
    @Column(name = "staff_note", length = 500)
    private String staffNote;

    /** Ghi chú của BM khi phê duyệt hoặc từ chối. */
    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
