package base.api.feature.shift.dto.response;

import base.api.shared.enums.UserRole;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AvailableEmployeeResponse {

    private Long employeeId;

    private String fullName;

    private String email;

    private Long branchId;

    private UserRole role;
}
