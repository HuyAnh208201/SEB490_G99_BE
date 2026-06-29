package base.api.feature.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InitiateForgotPasswordDto {
    @NotBlank(message = "Thông tin liên hệ (email hoặc tên đăng nhập) không được để trống")
    private String contactInfo; // email hoặc username
}
