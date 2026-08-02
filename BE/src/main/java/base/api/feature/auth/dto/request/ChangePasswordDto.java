package base.api.feature.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordDto {
    @NotBlank(message = "Current password is required.")
    @Size(max = 128, message = "Current password must be at most 128 characters.")
    private String oldPassword;

    @NotBlank(message = "New password is required.")
    @Size(min = 6, max = 128, message = "Password must be between 6 and 128 characters.")
    private String newPassword;

    @NotBlank(message = "Password confirmation is required.")
    @Size(max = 128, message = "Password confirmation must be at most 128 characters.")
    private String confirmNewPassword;
}
