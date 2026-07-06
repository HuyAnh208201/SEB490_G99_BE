package base.api.feature.purchaserequest.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PurchaseRequestSummaryResponse {
    private Long id;
    private String requestNumber;
    private LocalDateTime createdAt;
    private Long branchId;
    private String branchName;
    private Integer itemCount;
    private String status;
    private Long createdBy;
    private String createdByName;
}
