package base.api.feature.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateProfileDto {
    
    @NotBlank(message = "Họ không được để trống")
    private String firstName;
    
    @NotBlank(message = "Tên không được để trống")
    private String lastName;

    private String avatar;

    // Email is deliberately absent: it is the address the password reset link goes to,
    // so being able to change it means being able to hand account recovery to someone else.
    // Changing an email goes through an Admin. A client that sends "email" is ignored.

    @Pattern(regexp = "^(0|\\+84)[0-9]{9,10}$", message = "Số điện thoại không hợp lệ (VD: 0912345678 hoặc +84912345678)")
    private String phone;
    
    private LocalDateTime birthDate;
    
    @Pattern(regexp = "^(MALE|FEMALE|OTHER)$", message = "Giới tính không hợp lệ")
    private String gender;
}
