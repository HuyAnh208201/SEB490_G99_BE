package base.api.feature.shiftsession.dto.response;

import base.api.shared.enums.UserRole;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ShiftHandoverCandidateResponse {

    private Long employeeId;
    private String employeeName;
    private UserRole role;
    private Long shiftId;
    private LocalDateTime shiftStart;
    private LocalDateTime shiftEnd;
    private Boolean scheduledReplacement;
}
