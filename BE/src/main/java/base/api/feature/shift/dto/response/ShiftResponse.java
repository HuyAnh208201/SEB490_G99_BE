package base.api.feature.shift.dto.response;

import base.api.shared.enums.ShiftStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ShiftResponse {

    private Long id;

    private Long branchId;

    private Long createdBy;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private BigDecimal openingCash;

    private BigDecimal expectedCash;

    private BigDecimal actualCash;

    private BigDecimal difference;

    private ShiftStatus status;

    private Long approvedBy;

    /** ID staff đã đóng ca. */
    private Long closedBy;

    /** Ghi chú của staff khi đóng ca. */
    private String staffNote;

    /** Ghi chú của BM khi phê duyệt / từ chối. */
    private String reviewNote;

    private LocalDateTime createdAt;

    private List<AssignedEmployeeResponse> assignedEmployees = new ArrayList<>();
}
