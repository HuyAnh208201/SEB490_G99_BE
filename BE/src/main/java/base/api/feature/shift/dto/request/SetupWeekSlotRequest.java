package base.api.feature.shift.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SetupWeekSlotRequest {

    @NotNull(message = "Start time is required.")
    private LocalDateTime startTime;

    @NotNull(message = "End time is required.")
    private LocalDateTime endTime;

    /**
     * Cash float for the first slot of the day. Null means "use the configured
     * default"; ignored for any slot that is not the first of its day.
     */
    private BigDecimal openingCash;

    private List<Long> cashiers = new ArrayList<>();

    private List<Long> inventoryStaff = new ArrayList<>();
}
