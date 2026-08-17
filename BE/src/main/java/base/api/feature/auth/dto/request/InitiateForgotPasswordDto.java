package base.api.feature.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InitiateForgotPasswordDto {
    @NotBlank(message = "Contact information (email or username) is required.")
    private String contactInfo; // email hoặc username
    @Size(max = 255)
    private String frontendBaseUrl;
}
