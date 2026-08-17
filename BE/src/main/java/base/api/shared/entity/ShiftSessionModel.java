package base.api.shared.entity;

import base.api.shared.enums.ShiftSessionStatus;
import base.api.shared.enums.FundTransferMethod;
import base.api.shared.enums.UserRole;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "shift_sessions")
public class ShiftSessionModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shift_id", nullable = false)
    private Long shiftId;

    @Column(name = "shift_assignment_id")
    private Long shiftAssignmentId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private UserRole role;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ShiftSessionStatus status = ShiftSessionStatus.SCHEDULED;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "opening_confirmed")
    private Boolean openingConfirmed = false;

    @Column(name = "verification_confirmed")
    private Boolean verificationConfirmed = false;

    @Column(name = "handover_confirmed")
    private Boolean handoverConfirmed = false;

    @Column(name = "opening_note", columnDefinition = "TEXT")
    private String openingNote;

    @Column(name = "closing_note", columnDefinition = "TEXT")
    private String closingNote;

    @Column(name = "opening_fund_amount", precision = 15, scale = 2)
    private BigDecimal openingFundAmount;

    @Column(name = "opening_fund_received_from")
    private Long openingFundReceivedFrom;

    @Column(name = "opening_fund_received_at")
    private LocalDateTime openingFundReceivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "opening_fund_method", length = 20)
    private FundTransferMethod openingFundMethod;

    @Column(name = "transaction_count")
    private Integer transactionCount = 0;

    @Column(name = "cash_sales", precision = 15, scale = 2)
    private BigDecimal cashSales = BigDecimal.ZERO;

    @Column(name = "refund_amount", precision = 15, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Column(name = "expected_cash", precision = 15, scale = 2)
    private BigDecimal expectedCash;

    @Column(name = "actual_cash", precision = 15, scale = 2)
    private BigDecimal actualCash;

    @Column(name = "difference", precision = 15, scale = 2)
    private BigDecimal difference;

    @Column(name = "handover_to_employee_id")
    private Long handoverToEmployeeId;

    @Column(name = "handover_remark", columnDefinition = "TEXT")
    private String handoverRemark;

    @Column(name = "adjusted_products_count")
    private Integer adjustedProductsCount;

    @Column(name = "damaged_products_count")
    private Integer damagedProductsCount;

    @Column(name = "missing_products_count")
    private Integer missingProductsCount;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "manager_note", columnDefinition = "TEXT")
    private String managerNote;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
