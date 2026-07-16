package base.api.feature.auth.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserStatusDto {

    @NotNull(message = "Active flag is required.")
    private Boolean active;

    private String email;

    private String verificationCode;
}
