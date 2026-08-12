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

    // Không có email ở đây là chủ ý: email là địa chỉ nhận link đặt lại mật khẩu,
    // nên tự đổi được email nghĩa là tự chuyển được quyền khôi phục tài khoản.
    // Đổi email phải đi qua Admin. Client gửi kèm "email" thì Jackson bỏ qua.

    @Pattern(regexp = "^(0|\\+84)[0-9]{9,10}$", message = "Số điện thoại không hợp lệ (VD: 0912345678 hoặc +84912345678)")
    private String phone;
    
    private LocalDateTime birthDate;
    
    @Pattern(regexp = "^(MALE|FEMALE|OTHER)$", message = "Giới tính không hợp lệ")
    private String gender;
}
