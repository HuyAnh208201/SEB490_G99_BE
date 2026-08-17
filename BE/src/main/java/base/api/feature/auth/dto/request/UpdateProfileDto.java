package base.api.feature.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateProfileDto {

    @NotBlank(message = "First name is required.")
    @Size(max = 50, message = "First name must be at most 50 characters.")
    private String firstName;

    @NotBlank(message = "Last name is required.")
    @Size(max = 50, message = "Last name must be at most 50 characters.")
    private String lastName;

    private String avatar;

    @NotBlank(message = "Email is required.")
    @Email(message = "Email is invalid.")
    @Size(max = 255, message = "Email must be at most 255 characters.")
    private String email;

    /** Optional: blank/null = keep current phone. */
    @Pattern(
            regexp = "^$|^(0|\\+84)[0-9]{9,10}$",
            message = "Phone number is invalid (e.g. 0912345678 or +84912345678).")
    @Size(max = 20, message = "Phone number must be at most 20 characters.")
    private String phone;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    @Pattern(regexp = "^(MALE|FEMALE|OTHER)$", message = "Gender is invalid.")
    private String gender;
}
