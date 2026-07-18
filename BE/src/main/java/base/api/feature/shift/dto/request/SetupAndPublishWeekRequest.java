package base.api.feature.shift.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SetupAndPublishWeekRequest {

    @NotNull(message = "Branch is required.")
    private Long branchId;

    @NotNull(message = "Week start is required.")
    private LocalDate weekStart;

    @NotEmpty(message = "Slots are required.")
    @Valid
    private List<SetupWeekSlotRequest> slots = new ArrayList<>();
}
