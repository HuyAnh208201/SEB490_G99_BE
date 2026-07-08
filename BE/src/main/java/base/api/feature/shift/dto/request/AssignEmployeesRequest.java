package base.api.feature.shift.dto.request;

import base.api.shared.enums.UserRole;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AssignEmployeesRequest {

    @NotEmpty(message = "Employee list is required.")
    private List<Long> employeeIds;

    @NotNull(message = "Required role is required.")
    private UserRole requiredRole;
}
