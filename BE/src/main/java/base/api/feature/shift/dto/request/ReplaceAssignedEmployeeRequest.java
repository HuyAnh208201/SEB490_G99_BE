package base.api.feature.shift.dto.request;

import base.api.shared.enums.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReplaceAssignedEmployeeRequest {

    @NotNull(message = "Replacement employee is required.")
    private Long replacementEmployeeId;

    @NotNull(message = "Required role is required.")
    private UserRole requiredRole;
}
