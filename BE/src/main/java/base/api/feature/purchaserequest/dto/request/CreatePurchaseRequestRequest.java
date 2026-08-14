package base.api.feature.purchaserequest.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;

@Getter
@Setter
public class CreatePurchaseRequestRequest {

    @Size(max = 1000, message = "Notes must not exceed 1000 characters.")
    private String notes;

    @Valid
    private List<PurchaseRequestItemRequest> items = new ArrayList<>();

    private Boolean addAllRecommended = false;

    private LocalDate desiredReceiveDate;
}
