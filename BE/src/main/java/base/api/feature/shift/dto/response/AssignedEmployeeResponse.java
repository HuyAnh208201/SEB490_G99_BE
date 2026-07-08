package base.api.feature.shift.dto.response;

import base.api.shared.enums.UserRole;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AssignedEmployeeResponse {

    private Long assignmentId;

    private Long employeeId;

    private String fullName;

    private String email;

    private UserRole role;

    private LocalDateTime checkInAt;

    private LocalDateTime checkOutAt;
}
