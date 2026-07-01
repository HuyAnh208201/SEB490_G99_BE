package base.api.feature.branch.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateInventoryStaffRequest {

    @NotBlank(message = "Full name is required.")
    @Size(max = 255, message = "Full name must not exceed 255 characters.")
    private String fullName;

    @NotBlank(message = "Email is required.")
    @Email(message = "Invalid email.")
    private String email;

    @NotBlank(message = "Phone is required.")
    @Pattern(regexp = "^0[0-9]{9}$", message = "Invalid phone number.")
    private String phone;

    @NotBlank(message = "Password is required.")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,50}$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one digit and one special character."
    )
    private String password;

    @NotBlank(message = "Confirm password is required.")
    private String confirmPassword;

    private Long branchId;
}
