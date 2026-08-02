package base.api.feature.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompleteForgotPasswordDto {
    @NotBlank(message = "Token is required.")
    private String resetToken;

    @NotBlank(message = "New password is required.")
    @Size(min = 6, message = "Password must be at least 6 characters.")
    private String newPassword;

    @NotBlank(message = "Password confirmation is required.")
    private String confirmNewPassword;
}
