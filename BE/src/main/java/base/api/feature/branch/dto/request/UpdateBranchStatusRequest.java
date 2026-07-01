package base.api.feature.branch.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateBranchStatusRequest {

    @NotBlank(message = "Status is required.")
    private String status;
}
