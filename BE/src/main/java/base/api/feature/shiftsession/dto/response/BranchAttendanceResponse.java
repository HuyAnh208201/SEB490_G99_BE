package base.api.feature.shiftsession.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class BranchAttendanceResponse {
    private Long shiftId;
    private Long assignmentId;
    private Long sessionId;
    private Long employeeId;
    private String cashierName;
    private String role;
    private LocalDateTime shiftStartTime;
    private LocalDateTime shiftEndTime;
    private LocalDateTime checkInAt;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    /**
     * Minutes relative to shift startTime using check-in (or openedAt fallback).
     * Negative = early, positive = late, null if no check-in/open time.
     */
    private Long minutesLate;
}
