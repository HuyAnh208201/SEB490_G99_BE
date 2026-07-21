package base.api.feature.shift.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetupAndPublishWeekResponse {

    private int published;
    private WeeklyScheduleResponse schedule;
}
