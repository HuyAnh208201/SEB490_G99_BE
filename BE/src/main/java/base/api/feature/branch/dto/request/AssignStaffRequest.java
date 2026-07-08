package base.api.feature.branch.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignStaffRequest {

    @NotNull(message = "User ID is required.")
    private Long userId;

    @NotBlank(message = "Role is required.")
    private String role;

    private Boolean replaceExisting = false;
}
