package base.api.feature.shiftsession.dto.response;

import base.api.shared.enums.CashDifferenceStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PreviousShiftHandoverReportResponse {

    private Long sessionId;
    private Integer shiftNumber;
    private String employeeName;
    private LocalDateTime closedAt;
    private BigDecimal expectedCash;
    private BigDecimal actualCash;
    private BigDecimal difference;
    private CashDifferenceStatus differenceStatus;
    private String handoverRemark;
    private List<HighValueItemResponse> highValueItems = new ArrayList<>();
    private InventoryClosingSummaryResponse inventorySummary;
}
