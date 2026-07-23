package base.api.feature.shiftsession.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class ShiftBriefResponse {

    private Long id;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer shiftNumber;
    private BigDecimal openingCash;
}
