package base.api.feature.branchmanager.controller;

import base.api.feature.branchmanager.dto.response.BranchManagerDashboardResponse;
import base.api.feature.branchmanager.service.IBranchManagerDashboardService;
import base.api.shared.base.StubModuleController;
import base.api.shared.dto.TFUResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/branch-manager")
@Tag(name = "Branch Manager", description = "Vận hành chi nhánh")
public class BranchManagerController extends StubModuleController {

    @Autowired
    private IBranchManagerDashboardService branchManagerDashboardService;

    @Operation(summary = "Branch Dashboard")
    @PreAuthorize("@permissionChecker.has('BRANCH_DASHBOARD')")
    @GetMapping("dashboard")
    public ResponseEntity<TFUResponse<BranchManagerDashboardResponse>> dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return success(branchManagerDashboardService.getDashboard(from, to));
    }

    @Operation(summary = "Manage Branch/Staff Info")
    @PreAuthorize("@permissionChecker.has('MANAGE_BRANCH_STAFF_INFO')")
    @GetMapping("staff")
    public ResponseEntity<TFUResponse<Map<String, String>>> staffInfo() {
        return stub("branch-manager", "Manage Branch/Staff Info");
    }

    @Operation(summary = "Approve Cash Discrepancy")
    @PreAuthorize("@permissionChecker.has('APPROVE_CASH_DISCREPANCY')")
    @PostMapping("cash-discrepancies/approve")
    public ResponseEntity<TFUResponse<Map<String, String>>> approveCashDiscrepancy() {
        return stub("branch-manager", "Approve Cash Discrepancy");
    }

    @Operation(summary = "Branch Revenue/Promos (Branch Manager)")
    @PreAuthorize("@permissionChecker.has('BRANCH_REVENUE_PROMOS')")
    @GetMapping("revenue-promos")
    public ResponseEntity<TFUResponse<Map<String, String>>> revenuePromos() {
        return stub("branch-manager", "Branch Revenue/Promos");
    }

    @Operation(summary = "Supply Import Receipt: Approve")
    @PreAuthorize("@permissionChecker.has('SUPPLY_IMPORT_RECEIPT_APPROVE')")
    @PostMapping("supply-imports/approve")
    public ResponseEntity<TFUResponse<Map<String, String>>> approveSupplyImport() {
        return stub("branch-manager", "Supply Import Receipt: Approve");
    }

    @Operation(summary = "Shift Management: Create/Assign")
    @PreAuthorize("@permissionChecker.has('SHIFT_MANAGEMENT')")
    @GetMapping("shifts")
    public ResponseEntity<TFUResponse<Map<String, String>>> shifts() {
        return stub("branch-manager", "Shift Management: Create/Assign");
    }

    @Operation(summary = "Create Import Request")
    @PreAuthorize("@permissionChecker.has('CREATE_IMPORT_REQUEST')")
    @PostMapping("import-requests")
    public ResponseEntity<TFUResponse<Map<String, String>>> createImportRequest() {
        return stub("branch-manager", "Create Import Request");
    }
}
