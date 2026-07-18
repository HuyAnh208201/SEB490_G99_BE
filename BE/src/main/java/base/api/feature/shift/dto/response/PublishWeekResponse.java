package base.api.feature.shift.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PublishWeekResponse {

    private int published;
    private List<SkippedShift> skipped = new ArrayList<>();
    private WeeklyScheduleResponse schedule;

    @Getter
    @Setter
    public static class SkippedShift {
        private Long shiftId;
        private String reason;

        public SkippedShift() {
        }

        public SkippedShift(Long shiftId, String reason) {
            this.shiftId = shiftId;
            this.reason = reason;
        }
    }
}
