package base.api.feature.auth.dto.request;

import base.api.shared.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateUserByAdminDto {
    @NotBlank(message = "Username is required.")
    private String userName;

    @NotBlank(message = "Email is required.")
    @Email(message = "Email is invalid.")
    private String email;

    @NotBlank(message = "First name is required.")
    private String firstName;

    private String lastName;

    @NotBlank(message = "Phone number is required.")
    @Pattern(
            regexp = "^(0|\\+84)[0-9]{9,10}$",
            message = "Phone number is invalid (e.g. 0912345678 or +84912345678).")
    private String phone;

    @NotNull(message = "Role is required.")
    private UserRole role;

    private Long branchId;
}
