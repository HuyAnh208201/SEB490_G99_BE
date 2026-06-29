package base.api.feature.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyEmailDto {
    @NotBlank(message = "Token xác thực không được để trống")
    private String verificationToken;

    private String verificationCode;
}
