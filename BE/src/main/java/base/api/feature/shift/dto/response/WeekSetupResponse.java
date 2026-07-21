package base.api.feature.shift.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class WeekSetupResponse {

    private Long branchId;
    private LocalDate weekStart;
    private String operatingHours;
    private int maxEmployeesPerShift = 3;
    private List<AvailableEmployeeResponse> cashiers = new ArrayList<>();
    private List<AvailableEmployeeResponse> inventoryStaff = new ArrayList<>();
    private List<WeekSetupSlotResponse> slots = new ArrayList<>();
}
