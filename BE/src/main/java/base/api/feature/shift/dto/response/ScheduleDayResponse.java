package base.api.feature.shift.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ScheduleDayResponse {

    private LocalDate date;

    private List<ShiftResponse> shifts = new ArrayList<>();
}
