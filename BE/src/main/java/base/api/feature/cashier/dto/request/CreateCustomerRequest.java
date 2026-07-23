package base.api.feature.cashier.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Cashier tạo nhanh khách mới ngay tại quầy khi tra cứu không ra.
 * Chỉ cần tên và SĐT — email, mật khẩu do hệ thống sinh.
 */
@Data
public class CreateCustomerRequest {

    @NotBlank(message = "Customer name is required.")
    @Size(max = 100, message = "Customer name must be at most 100 characters.")
    private String fullName;

    @NotBlank(message = "Phone number is required.")
    @Pattern(regexp = "^[0-9+][0-9 .-]{7,19}$", message = "Enter a valid phone number.")
    private String phone;
}
