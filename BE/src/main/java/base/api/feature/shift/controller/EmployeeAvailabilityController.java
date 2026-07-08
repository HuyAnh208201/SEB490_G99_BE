package base.api.feature.shift.controller;

import base.api.feature.shift.dto.response.AvailableEmployeeResponse;
import base.api.feature.shift.service.IShiftService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import base.api.shared.enums.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/employees")
@Tag(name = "Employees", description = "Employee availability for shift scheduling")
public class EmployeeAvailabilityController extends BaseAPIController {

    @Autowired
    private IShiftService shiftService;

    @Operation(summary = "Find available employees")
    @PreAuthorize("@permissionChecker.has('SHIFT_MANAGEMENT')")
    @GetMapping("/available")
    public ResponseEntity<TFUResponse<List<AvailableEmployeeResponse>>> findAvailableEmployees(
            @RequestParam Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            @RequestParam UserRole requiredRole) {
        return success(shiftService.findAvailableEmployees(branchId, date, startTime, endTime, requiredRole));
    }
}
