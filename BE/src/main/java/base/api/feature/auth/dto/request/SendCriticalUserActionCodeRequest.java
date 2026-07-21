package base.api.feature.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendCriticalUserActionCodeRequest {

    @NotBlank(message = "Email is required.")
    private String email;

    @NotBlank(message = "Action type is required.")
    private String actionType;
}
