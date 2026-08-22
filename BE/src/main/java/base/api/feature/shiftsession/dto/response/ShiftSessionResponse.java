package base.api.feature.shiftsession.dto.response;

import base.api.shared.enums.CashDifferenceStatus;
import base.api.shared.enums.FundTransferMethod;
import base.api.shared.enums.ShiftSessionStatus;
import base.api.shared.enums.UserRole;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ShiftSessionResponse {

    private Long id;
    private Long shiftId;
    private Long shiftAssignmentId;
    private Long employeeId;
    /** Cashier who opened the session (same as employeeId when status is OPEN+). */
    private Long openedBy;
    private UserRole role;
    private Long branchId;
    private ShiftSessionStatus status;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private Boolean openingConfirmed;
    /** Same value as openingConfirmed — reserved for shift history screens. */
    private Boolean openingFundConfirmed;
    private String openingFundStatus;
    private Boolean verificationConfirmed;
    private Boolean handoverConfirmed;
    private String openingNote;
    private String closingNote;
    private BigDecimal openingFundAmount;
    private String openingFundReceivedFromName;
    private Long openingFundReceivedFromEmployeeId;
    private LocalDateTime openingFundReceivedAt;
    private FundTransferMethod openingFundMethod;
    private List<ShiftHandoverCandidateResponse> openingFundSources = new ArrayList<>();
    private Integer transactionCount;
    private BigDecimal cashSales;
    private BigDecimal refundAmount;
    private BigDecimal expectedCash;
    private BigDecimal actualCash;
    private BigDecimal difference;
    private CashDifferenceStatus differenceStatus;
    private String cashierExplanation;
    private Long handoverToEmployeeId;
    private String handoverToEmployeeName;
    private List<ShiftHandoverCandidateResponse> handoverCandidates = new ArrayList<>();
    private Boolean earlyClose;
    private String handoverRemark;
    private Integer adjustedProductsCount;
    private Integer damagedProductsCount;
    private Integer missingProductsCount;

    /** BM cash-discrepancy review outcome (set once the session is approved/rejected). */
    private String reviewNote;
    private String reviewedByName;

    private ShiftBriefResponse shift;
    private String employeeName;
    private String branchName;

    /** From BM-published shift assignment (Shift Management module). */
    private Boolean checkedIn;
    private LocalDateTime checkInAt;

    private List<HighValueItemResponse> highValueItems = new ArrayList<>();
    private InventoryClosingSummaryResponse inventorySummary;
    private List<ShiftSessionApprovalHistoryResponse> approvalHistory = new ArrayList<>();

    /** Read-only handover summary from the last closed cashier session (opening flow only). */
    private PreviousShiftHandoverReportResponse previousShiftReport;

    /** Product qty deltas vs the previous closed cashier shift (closing + BM review). */
    private List<PreviousShiftProductVarianceResponse> previousShiftProductVariance = new ArrayList<>();

    private ShiftSessionTransactionSummaryResponse transactionSummary;

    /** Derived from branch hours: 0 = first slot of the day. */
    private Integer currentSlotIndex;
    private String currentSlotLabel;
    private String currentSlotStart;
    private String currentSlotEnd;
    /** True when system time is outside branch hours (test mode may still allow opening). */
    private Boolean outsideOperatingHours;

    private Long approvedBy;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private String managerNote;

    /** True when this cashier joined a shift already opened by a colleague. */
    private Boolean joinedExistingShift;
    private Long shiftOpenedByEmployeeId;
    private String shiftOpenedByName;

    /** True when any high-value product count differs from expected at closing. */
    private Boolean hasProductDiscrepancy;
    private Integer productDiscrepancyCount;
}
