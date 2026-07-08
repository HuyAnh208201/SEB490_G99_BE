package base.api.feature.shift.service;

import base.api.feature.shift.dto.request.AssignEmployeesRequest;
import base.api.feature.shift.dto.request.CreateShiftRequest;
import base.api.feature.shift.dto.request.ReplaceAssignedEmployeeRequest;
import base.api.feature.shift.dto.request.UpdateShiftRequest;
import base.api.feature.shift.dto.response.AvailableEmployeeResponse;
import base.api.feature.shift.dto.response.ShiftResponse;
import base.api.feature.shift.dto.response.WeeklyScheduleResponse;
import base.api.shared.enums.UserRole;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface IShiftService {

    ShiftResponse create(CreateShiftRequest request);

    ShiftResponse update(Long id, UpdateShiftRequest request);

    void delete(Long id);

    ShiftResponse getById(Long id);

    List<ShiftResponse> getAll(Long branchId);

    ShiftResponse assignEmployees(Long shiftId, AssignEmployeesRequest request);

    void removeEmployee(Long shiftId, Long employeeId);

    ShiftResponse replaceEmployee(Long shiftId, Long employeeId, ReplaceAssignedEmployeeRequest request);

    ShiftResponse publish(Long id);

    WeeklyScheduleResponse getWeeklySchedule(Long branchId, LocalDate weekStart);

    List<AvailableEmployeeResponse> findAvailableEmployees(
            Long branchId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            UserRole requiredRole);
}
