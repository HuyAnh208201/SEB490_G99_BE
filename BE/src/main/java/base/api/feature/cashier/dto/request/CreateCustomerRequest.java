package base.api.feature.cashier.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Quick customer create at the counter when lookup finds no match.
 * Name and phone are required; email is optional.
 */
@Data
public class CreateCustomerRequest {

    @NotBlank(message = "Customer name is required.")
    @Size(max = 100, message = "Customer name must be at most 100 characters.")
    private String fullName;

    @NotBlank(message = "Phone number is required.")
    @Pattern(
            regexp = "^(0|\\+84)[0-9]{9,10}$",
            message = "Enter a valid phone number (e.g. 0912345678 or +84912345678).")
    @Size(max = 20, message = "Phone number must be at most 20 characters.")
    private String phone;

    @Email(message = "Email is invalid.")
    @Size(max = 255, message = "Email must be at most 255 characters.")
    private String email;
}
