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
    private LocalDate requestDate;
    private LocalDateTime createdAt;
    private String notes;
    private List<PurchaseRequestDetailResponse> items = new ArrayList<>();
}
