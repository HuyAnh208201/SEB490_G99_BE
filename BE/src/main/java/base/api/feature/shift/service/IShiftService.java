package base.api.feature.shift.service;

import base.api.feature.shift.dto.request.AssignEmployeesRequest;
import base.api.feature.shift.dto.request.AssignSlotRequest;
import base.api.feature.shift.dto.request.CreateShiftRequest;
import base.api.feature.shift.dto.request.ReplaceAssignedEmployeeRequest;
import base.api.feature.shift.dto.request.SetupAndPublishWeekRequest;
import base.api.feature.shift.dto.request.UpdateOpeningCashRequest;
import base.api.feature.shift.dto.request.UpdateShiftRequest;
import base.api.feature.shift.dto.request.WeekScheduleRequest;
import base.api.feature.shift.dto.response.AvailableEmployeeResponse;
import base.api.feature.shift.dto.response.CopyWeekResponse;
import base.api.feature.shift.dto.response.PublishWeekResponse;
import base.api.feature.shift.dto.response.SetupAndPublishWeekResponse;
import base.api.feature.shift.dto.response.ShiftResponse;
import base.api.feature.shift.dto.response.WeekSetupResponse;
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

    /**
     * Upsert a DRAFT shift for the given time window and sync cashier / inventory-staff
     * assignments. Empty both lists clears (deletes) an existing draft slot.
     */
    ShiftResponse assignToSlot(AssignSlotRequest request);

    PublishWeekResponse publishWeek(WeekScheduleRequest request);

    CopyWeekResponse copyPreviousWeek(WeekScheduleRequest request);

    WeekSetupResponse getWeekSetup(Long branchId, LocalDate weekStart);

    SetupAndPublishWeekResponse setupAndPublishWeek(SetupAndPublishWeekRequest request);

    WeeklyScheduleResponse getWeeklySchedule(Long branchId, LocalDate weekStart);

    List<AvailableEmployeeResponse> findAvailableEmployees(
            Long branchId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            UserRole requiredRole);

    List<ShiftResponse> getMyShifts();

    WeeklyScheduleResponse getMyWeeklySchedule(LocalDate weekStart);

    ShiftResponse checkIn(Long shiftId);

    /**
     * Branch Manager adjusts the cash float by hand. Allowed only while the shift is
     * DRAFT or PUBLISHED — anything already closed or reviewed keeps its figures.
     */
    ShiftResponse updateOpeningCash(Long shiftId, UpdateOpeningCashRequest request);
}
