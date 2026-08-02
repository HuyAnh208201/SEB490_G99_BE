package base.api.feature.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyEmailDto {
    @NotBlank(message = "Verification token is required.")
    private String verificationToken;

    private String verificationCode;
}
