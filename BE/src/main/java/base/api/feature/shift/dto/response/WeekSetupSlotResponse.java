package base.api.feature.shift.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class WeekSetupSlotResponse {

    private LocalDate date;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int slotIndex;
    private boolean first;
    private boolean last;
    /** Cash float already saved on the shift, or the suggested default for an unsaved first slot. */
    private BigDecimal openingCash;
    private boolean published;
    private boolean readOnly;
    private Long shiftId;
    private String status;
    private List<Long> cashiers = new ArrayList<>();
    private List<Long> inventoryStaff = new ArrayList<>();
    private List<AssignedEmployeeResponse> assignedEmployees = new ArrayList<>();
}
