package base.api.feature.branch.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendBranchSuspendCodeRequest {

    @NotBlank(message = "Email is required.")
    @Email(message = "Invalid email.")
    private String email;
}
