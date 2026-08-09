package base.api.feature.shift.controller;

import base.api.feature.shift.dto.request.AssignEmployeesRequest;
import base.api.feature.shift.dto.request.AssignSlotRequest;
import base.api.feature.shift.dto.request.CreateShiftRequest;
import base.api.feature.shift.dto.request.ReplaceAssignedEmployeeRequest;
import base.api.feature.shift.dto.request.SetupAndPublishWeekRequest;
import base.api.feature.shift.dto.request.UpdateOpeningCashRequest;
import base.api.feature.shift.dto.request.UpdateShiftRequest;
import base.api.feature.shift.dto.request.WeekScheduleRequest;
import base.api.feature.shift.dto.response.CopyWeekResponse;
import base.api.feature.shift.dto.response.PublishWeekResponse;
import base.api.feature.shift.dto.response.SetupAndPublishWeekResponse;
import base.api.feature.shift.dto.response.ShiftResponse;
import base.api.feature.shift.dto.response.WeekSetupResponse;
import base.api.feature.shift.dto.response.WeeklyScheduleResponse;
import base.api.feature.shift.service.IShiftService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/shifts")
@Tag(name = "Shifts", description = "Branch shift scheduling and employee assignment")
public class ShiftController extends BaseAPIController {

    @Autowired
    private IShiftService shiftService;

    @Operation(summary = "Create shift")
    @PreAuthorize("@permissionChecker.has('SHIFT_MANAGEMENT')")
    @PostMapping
    public ResponseEntity<TFUResponse<ShiftResponse>> create(@Valid @RequestBody CreateShiftRequest request) {
        ShiftResponse data = shiftService.create(request);
        TFUResponse<ShiftResponse> body = new TFUResponse<>(
                true, data, "Shift created successfully.", HttpStatus.CREATED.value(), null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "List shifts")
    @PreAuthorize("@permissionChecker.has('SHIFT_MANAGEMENT')")
    @GetMapping
    public ResponseEntity<TFUResponse<List<ShiftResponse>>> getAll(@RequestParam(required = false) Long branchId) {
        return success(shiftService.getAll(branchId));
    }

    @Operation(summary = "Get shift detail")
    @PreAuthorize("@permissionChecker.has('SHIFT_MANAGEMENT')")
    @GetMapping("/{id}")
    public ResponseEntity<TFUResponse<ShiftResponse>> getById(@PathVariable Long id) {
        return success(shiftService.getById(id));
    }

    @Operation(summary = "Update shift")
    @PreAuthorize("@permissionChecker.has('SHIFT_MANAGEMENT')")
    @PutMapping("/{id}")
    public ResponseEntity<TFUResponse<ShiftResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateShiftRequest request) {
        return success(shiftService.update(id, request), "Shift updated successfully.");
    }

    @Operation(
            summary = "Update opening cash float",
            description = "The first slot of each day already gets a float from configuration "
                    + "(shift.default-opening-cash). Use this to adjust it by hand: holidays, a special "
                    + "float, or a handover that did not match. Allowed while the shift is DRAFT or "
                    + "PUBLISHED only. Note: if the previous shift of the same day closes after this "
                    + "call, its handover amount overwrites the value set here."
    )
    @PreAuthorize("@permissionChecker.has('SHIFT_MANAGEMENT')")
    @PatchMapping("/{id}/opening-cash")
    public ResponseEntity<TFUResponse<ShiftResponse>> updateOpeningCash(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOpeningCashRequest request) {
        return success(shiftService.updateOpeningCash(id, request), "Opening cash updated successfully.");
    }

    @Operation(summary = "Delete shift")
    @PreAuthorize("@permissionChecker.has('SHIFT_MANAGEMENT')")
    @DeleteMapping("/{id}")
    public ResponseEntity<TFUResponse<Void>> delete(@PathVariable Long id) {
        shiftService.delete(id);
        return success(null, "Shift deleted successfully.");
    }

    @Operation(summary = "Publish shift")
    @PreAuthorize("@permissionChecker.has('SHIFT_MANAGEMENT')")
    @PutMapping("/{id}/publish")
    public ResponseEntity<TFUResponse<ShiftResponse>> publish(@PathVariable Long id) {
        return success(shiftService.publish(id), "Shift published successfully.");
    }

    @Operation(summary = "Assign staff to a schedule slot (creates DRAFT only when saving)")
    @PreAuthorize("@permissionChecker.has('SHIFT_MANAGEMENT')")
    @PostMapping("/slot/assign")
    public ResponseEntity<TFUResponse<ShiftResponse>> assignToSlot(
            @Valid @RequestBody AssignSlotRequest request) {
        ShiftResponse data = shiftService.assignToSlot(request);
        String message = data == null ? "Slot cleared." : "Slot assignments saved.";
        return success(data, message);
    }

    @Operation(summary = "Publish all staffing-ready DRAFT shifts in a week")
    @PreAuthorize("@permissionChecker.has('SHIFT_MANAGEMENT')")
    @PutMapping("/week/publish")
    public ResponseEntity<TFUResponse<PublishWeekResponse>> publishWeek(
            @Valid @RequestBody WeekScheduleRequest request) {
        PublishWeekResponse data = shiftService.publishWeek(request);
        return success(data, "Week publish completed.");
    }

    @Operation(summary = "Copy previous week assignments into empty slots of the target week")
    @PreAuthorize("@permissionChecker.has('SHIFT_MANAGEMENT')")
    @PostMapping("/week/copy")
    public ResponseEntity<TFUResponse<CopyWeekResponse>> copyPreviousWeek(
            @Valid @RequestBody WeekScheduleRequest request) {
        CopyWeekResponse data = shiftService.copyPreviousWeek(request);
        return success(data, "Previous week copy completed.");
    }

    @Operation(summary = "Load week setup grid with staff candidates")
    @PreAuthorize("@permissionChecker.has('SHIFT_MANAGEMENT')")
    @GetMapping("/week/setup")
    public ResponseEntity<TFUResponse<WeekSetupResponse>> getWeekSetup(
            @RequestParam Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return success(shiftService.getWeekSetup(branchId, weekStart));
    }

    @Operation(summary = "Assign every unpublished slot for a week and publish atomically")
    @PreAuthorize("@permissionChecker.has('SHIFT_MANAGEMENT')")
    @PostMapping("/week/setup-and-publish")
    public ResponseEntity<TFUResponse<SetupAndPublishWeekResponse>> setupAndPublishWeek(
            @Valid @RequestBody SetupAndPublishWeekRequest request) {
        return success(shiftService.setupAndPublishWeek(request), "Week setup published successfully.");
    }

    @Operation(summary = "Assign employees to shift")
    @PreAuthorize("@permissionChecker.has('SHIFT_MANAGEMENT')")
    @PostMapping("/{shiftId}/assign")
    public ResponseEntity<TFUResponse<ShiftResponse>> assignEmployees(
            @PathVariable Long shiftId,
            @Valid @RequestBody AssignEmployeesRequest request) {
        return success(shiftService.assignEmployees(shiftId, request), "Employees assigned successfully.");
    }

    @Operation(summary = "Remove employee from shift")
    @PreAuthorize("@permissionChecker.has('SHIFT_MANAGEMENT')")
    @DeleteMapping("/{shiftId}/assign/{employeeId}")
    public ResponseEntity<TFUResponse<Void>> removeEmployee(
            @PathVariable Long shiftId,
            @PathVariable Long employeeId) {
        shiftService.removeEmployee(shiftId, employeeId);
        return success(null, "Employee removed from shift successfully.");
    }

    @Operation(summary = "Replace assigned employee")
    @PreAuthorize("@permissionChecker.has('SHIFT_MANAGEMENT')")
    @PutMapping("/{shiftId}/assign/{employeeId}")
    public ResponseEntity<TFUResponse<ShiftResponse>> replaceEmployee(
            @PathVariable Long shiftId,
            @PathVariable Long employeeId,
            @Valid @RequestBody ReplaceAssignedEmployeeRequest request) {
        return success(
                shiftService.replaceEmployee(shiftId, employeeId, request),
                "Assigned employee replaced successfully.");
    }

    @Operation(summary = "View weekly schedule")
    @PreAuthorize("@permissionChecker.has('SHIFT_MANAGEMENT')")
    @GetMapping("/weekly")
    public ResponseEntity<TFUResponse<WeeklyScheduleResponse>> getWeeklySchedule(
            @RequestParam Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return success(shiftService.getWeeklySchedule(branchId, weekStart));
    }

    @Operation(summary = "My assigned shifts (cashier — published schedule from Branch Manager)")
    @PreAuthorize("@permissionChecker.has('MY_SHIFTS')")
    @GetMapping("/my")
    public ResponseEntity<TFUResponse<List<ShiftResponse>>> getMyShifts() {
        return success(shiftService.getMyShifts());
    }

    @Operation(summary = "My weekly published schedule (cashier / inventory staff)")
    @PreAuthorize("@permissionChecker.has('MY_SHIFTS')")
    @GetMapping("/my/weekly")
    public ResponseEntity<TFUResponse<WeeklyScheduleResponse>> getMyWeeklySchedule(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return success(shiftService.getMyWeeklySchedule(weekStart));
    }

    @Operation(summary = "Check in to assigned shift")
    @PreAuthorize("@permissionChecker.has('MY_SHIFTS')")
    @PatchMapping("/{shiftId}/check-in")
    public ResponseEntity<TFUResponse<ShiftResponse>> checkIn(@PathVariable Long shiftId) {
        return success(shiftService.checkIn(shiftId), "Checked in successfully.");
    }
}
