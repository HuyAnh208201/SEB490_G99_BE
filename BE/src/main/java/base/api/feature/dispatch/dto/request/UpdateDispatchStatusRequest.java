package base.api.feature.dispatch.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDispatchStatusRequest {

    /** PREPARING / DELIVERING / RECEIVED. */
    @NotBlank(message = "Status is required.")
    private String status;
}
