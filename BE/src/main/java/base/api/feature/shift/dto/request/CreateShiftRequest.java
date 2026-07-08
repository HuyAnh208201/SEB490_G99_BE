package base.api.feature.shift.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class CreateShiftRequest {

    @NotNull(message = "Branch is required.")
    private Long branchId;

    @NotNull(message = "Start time is required.")
    private LocalDateTime startTime;

    @NotNull(message = "End time is required.")
    private LocalDateTime endTime;

    private BigDecimal openingCash;

    private BigDecimal expectedCash;
}
