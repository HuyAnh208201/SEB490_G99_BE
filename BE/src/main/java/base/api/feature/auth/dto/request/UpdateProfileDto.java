package base.api.feature.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateProfileDto {
    
    @NotBlank(message = "Họ không được để trống")
    private String firstName;
    
    @NotBlank(message = "Tên không được để trống")
    private String lastName;

    private String avatar;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    @Pattern(regexp = "^(0|\\+84)[0-9]{9,10}$", message = "Số điện thoại không hợp lệ (VD: 0912345678 hoặc +84912345678)")
    private String phone;
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthDate;
    
    @Pattern(regexp = "^(MALE|FEMALE|OTHER)$", message = "Giới tính không hợp lệ")
    private String gender;
}
