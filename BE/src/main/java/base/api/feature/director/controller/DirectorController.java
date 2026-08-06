package base.api.feature.director.controller;

import base.api.feature.director.dto.response.DirectorDashboardResponse;
import base.api.feature.director.service.IDirectorDashboardService;
import base.api.shared.base.StubModuleController;
import base.api.shared.dto.TFUResponse;
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
import java.util.Map;

@RestController
@RequestMapping("/api/director")
@Tag(name = "Director", description = "Dashboard & báo cáo cấp Director")
public class DirectorController extends StubModuleController {

    @Autowired
    private IDirectorDashboardService directorDashboardService;

    @Operation(summary = "Director Dashboard — executive portfolio")
    @PreAuthorize("@permissionChecker.has('DIRECTOR_DASHBOARD')")
    @GetMapping("dashboard")
    public ResponseEntity<TFUResponse<DirectorDashboardResponse>> dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return success(directorDashboardService.getDashboard(from, to));
    }

    @Operation(summary = "Branch List (Director)")
    @PreAuthorize("@permissionChecker.has('BRANCH_LIST_DIRECTOR')")
    @GetMapping("branches")
    public ResponseEntity<TFUResponse<Map<String, String>>> branchList() {
        return stub("director", "Branch List Screen (Director)");
    }

    @Operation(summary = "Business Performance Reports")
    @PreAuthorize("@permissionChecker.has('BUSINESS_PERFORMANCE_REPORTS')")
    @GetMapping("reports/performance")
    public ResponseEntity<TFUResponse<Map<String, String>>> performanceReports() {
        return stub("director", "Business Performance Reports");
    }

    @Operation(summary = "Strategic Planning Overview")
    @PreAuthorize("@permissionChecker.has('STRATEGIC_PLANNING_OVERVIEW')")
    @GetMapping("planning")
    public ResponseEntity<TFUResponse<Map<String, String>>> strategicPlanning() {
        return stub("director", "Strategic Planning Overview");
    }

    @Operation(summary = "Branch Revenue/Promos (Director)")
    @PreAuthorize("@permissionChecker.has('BRANCH_REVENUE_PROMOS')")
    @GetMapping("revenue-promos")
    public ResponseEntity<TFUResponse<Map<String, String>>> revenuePromos() {
        return stub("director", "Branch Revenue/Promos");
    }
}
