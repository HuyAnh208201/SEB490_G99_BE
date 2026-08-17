package base.api.feature.purchaserequest.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PurchaseRequestResponse {
    private Long id;
    private String requestNumber;
    private Long branchId;
    private String branchName;
    private Long createdBy;
    private String createdByName;
    private String status;
    private Long approvedBy;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private String rejectReason;
    private LocalDate requestDate;
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
    private LocalDate desiredReceiveDate;
    private Long supplementalForReceiptId;
    private String notes;
    private List<PurchaseRequestDetailResponse> items = new ArrayList<>();
}
