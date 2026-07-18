package base.api.feature.shift.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class WeekScheduleRequest {

    @NotNull(message = "Branch is required.")
    private Long branchId;

    /** Monday (or any date — service normalizes to that week's Monday) of the target week. */
    @NotNull(message = "Week start is required.")
    private LocalDate weekStart;
}
