package base.api.feature.shift.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class WeeklyScheduleResponse {

    private Long branchId;

    private LocalDate weekStart;

    /** Branch operating hours string (e.g. "08:00 - 22:00"), when available. */
    private String operatingHours;

    private List<ScheduleDayResponse> days = new ArrayList<>();
}
