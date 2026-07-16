package base.api.feature.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CriticalUserActionRequest {

    @NotBlank(message = "Email is required.")
    private String email;

    @NotBlank(message = "Verification code is required.")
    private String verificationCode;
}
