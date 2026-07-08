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

    private List<ScheduleDayResponse> days = new ArrayList<>();
}
