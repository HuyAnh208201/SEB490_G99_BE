package base.api.feature.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InitiateForgotPasswordDto {
    @NotBlank(message = "Contact information (email or username) is required.")
    private String contactInfo;
}
