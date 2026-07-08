package base.api.feature.branch.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateBranchRequest {

    @NotBlank(message = "Branch name is required.")
    @Size(max = 255, message = "Branch name must not exceed 255 characters.")
    private String name;

    @NotBlank(message = "Address is required.")
    @Size(max = 255, message = "Address must not exceed 255 characters.")
    private String address;

    @NotBlank(message = "Phone is required.")
    @Pattern(regexp = "^0[0-9]{9}$", message = "Invalid phone number.")
    private String phone;

    @NotBlank(message = "Operating hours is required.")
    @Size(max = 255, message = "Operating hours must not exceed 255 characters.")
    private String operatingHours;

    @NotBlank(message = "Status is required.")
    private String status;
}
