package base.api.feature.shiftsession.dto.response;

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
    private Long employeeId;
    private UserRole role;
    private Long branchId;
    private ShiftSessionStatus status;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private Boolean openingConfirmed;
    private Boolean verificationConfirmed;
    private Boolean handoverConfirmed;
    private String openingNote;
    private String closingNote;
    private BigDecimal openingFundAmount;
    private String openingFundReceivedFromName;
    private LocalDateTime openingFundReceivedAt;
    private Integer transactionCount;
    private BigDecimal cashSales;
    private BigDecimal refundAmount;
    private BigDecimal expectedCash;
    private BigDecimal actualCash;
    private BigDecimal difference;
    private Long handoverToEmployeeId;
    private String handoverToEmployeeName;
    private String handoverRemark;
    private Integer adjustedProductsCount;
    private Integer damagedProductsCount;
    private Integer missingProductsCount;

    private ShiftBriefResponse shift;
    private String employeeName;
    private String branchName;

    /** From BM-published shift assignment (Shift Management module). */
    private Boolean checkedIn;
    private LocalDateTime checkInAt;

    private List<HighValueItemResponse> highValueItems = new ArrayList<>();
    private InventoryClosingSummaryResponse inventorySummary;
}
