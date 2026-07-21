package base.api.feature.shift.controller;

import base.api.feature.shift.dto.request.AssignEmployeesRequest;
import base.api.feature.shift.dto.request.AssignSlotRequest;
import base.api.feature.shift.dto.request.CreateShiftRequest;
import base.api.feature.shift.dto.request.ReplaceAssignedEmployeeRequest;
import base.api.feature.shift.dto.request.SetupAndPublishWeekRequest;
import base.api.feature.shift.dto.request.CloseShiftRequest;
import base.api.feature.shift.dto.request.ReviewShiftRequest;
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
import org.springframework.web.bind.annotation.PathVariable;
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

    // =========================================================================
    // Đóng ca và đối soát tiền
    // =========================================================================

    @Operation(
            summary = "Đóng ca (Staff)",
            description = "Cashier hoặc Inventory Staff đóng ca cuối ngày. " +
                    "Nhập số tiền thực đếm được. Hệ thống tự tính chênh lệch = actual - expected. " +
                    "Ca chuyển sang trạng thái CLOSED, chờ BM đối soát."
    )
    @PreAuthorize("@permissionChecker.has('CASHIER_CLOSE_SHIFT')")
    @PutMapping("/{id}/close")
    public ResponseEntity<TFUResponse<ShiftResponse>> closeShift(
            @PathVariable Long id,
            @Valid @RequestBody CloseShiftRequest request) {
        ShiftResponse result = shiftService.closeShift(id, request);
        return success(result, "Đóng ca thành công. Chênh lệch: " + result.getDifference() + " VNĐ. Chờ BM phê duyệt.");
    }

    @Operation(
            summary = "Phê duyệt chênh lệch tiền ca (Branch Manager)",
            description = "BM xác nhận chênh lệch giữa tiền thực và tiền kỳ vọng. Ca chuyển sang APPROVED."
    )
    @PreAuthorize("@permissionChecker.has('APPROVE_CASH_DISCREPANCY')")
    @PutMapping("/{id}/approve")
    public ResponseEntity<TFUResponse<ShiftResponse>> approveShift(
            @PathVariable Long id,
            @RequestBody ReviewShiftRequest request) {
        return success(shiftService.approveShift(id, request), "Phê duyệt chênh lệch thành công.");
    }

    @Operation(
            summary = "Từ chối — yêu cầu đếm lại (Branch Manager)",
            description = "BM từ chối kết quả kiểm kê. Staff sẽ phải đếm lại và nộp lại. Ca chuyển sang REJECTED."
    )
    @PreAuthorize("@permissionChecker.has('APPROVE_CASH_DISCREPANCY')")
    @PutMapping("/{id}/reject")
    public ResponseEntity<TFUResponse<ShiftResponse>> rejectShift(
            @PathVariable Long id,
            @RequestBody ReviewShiftRequest request) {
        return success(shiftService.rejectShift(id, request), "Từ chối. Staff cần đếm lại và nộp lại.");
    }
}
