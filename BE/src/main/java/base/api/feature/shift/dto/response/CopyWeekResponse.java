package base.api.feature.shift.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CopyWeekResponse {

    private int copied;
    private int skipped;
    private List<String> conflicts = new ArrayList<>();
    private WeeklyScheduleResponse schedule;
}
