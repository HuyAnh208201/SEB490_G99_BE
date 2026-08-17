package base.api.shared.entity;

import base.api.shared.converter.PurchaseRequestStatusConverter;
import base.api.shared.enums.PurchaseRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "purchase_requests")
public class PurchaseRequestModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Convert(converter = PurchaseRequestStatusConverter.class)
    @Column(length = 255)
    private PurchaseRequestStatus status = PurchaseRequestStatus.DRAFT;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "desired_receive_date")
    private LocalDate desiredReceiveDate;

    @Column(name = "supplemental_for_receipt_id")
    private Long supplementalForReceiptId;
}
