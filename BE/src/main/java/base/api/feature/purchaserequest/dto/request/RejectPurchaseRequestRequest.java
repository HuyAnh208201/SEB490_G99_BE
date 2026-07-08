package base.api.feature.purchaserequest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectPurchaseRequestRequest {

    @NotBlank(message = "Reject reason is required.")
    @Size(max = 1000, message = "Reject reason must not exceed 1000 characters.")
    private String reason;
}
