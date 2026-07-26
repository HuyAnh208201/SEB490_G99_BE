package base.api.feature.branch.controller;

import base.api.feature.branch.dto.request.AssignStaffRequest;
import base.api.feature.branch.dto.request.SendBranchSuspendCodeRequest;
import base.api.feature.branch.dto.request.CreateBranchManagerRequest;
import base.api.feature.branch.dto.request.CreateBranchRequest;
import base.api.feature.branch.dto.request.CreateCashierRequest;
import base.api.feature.branch.dto.request.CreateInventoryStaffRequest;
import base.api.feature.branch.dto.request.UpdateBranchRequest;
import base.api.feature.branch.dto.request.UpdateBranchStatusRequest;
import base.api.feature.branch.dto.response.BranchResponse;
import base.api.feature.branch.dto.response.UserResponse;
import base.api.feature.branch.service.IBranchService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.dto.PageResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@Tag(name = "Branches", description = "Branch management and branch staff accounts")
public class BranchController extends BaseAPIController {

    @Autowired
    private IBranchService branchService;

    @Operation(summary = "Create branch")
    @PreAuthorize("@permissionChecker.has('MANAGE_BRANCH_INFORMATION')")
    @PostMapping
    public ResponseEntity<TFUResponse<BranchResponse>> create(@Valid @RequestBody CreateBranchRequest request) {
        BranchResponse data = branchService.createBranch(request);
        TFUResponse<BranchResponse> body = new TFUResponse<>(
                true, data, "Branch created successfully.", HttpStatus.CREATED.value(), null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Get branch list")
    @PreAuthorize("@permissionChecker.hasAny('BRANCH_LIST_ADMIN', 'BRANCH_LIST_DIRECTOR', 'BRANCH_DASHBOARD')")
    @GetMapping
    public ResponseEntity<TFUResponse<List<BranchResponse>>> getAll() {
        return success(branchService.getAllBranches());
    }

    @Operation(summary = "Search, filter and paginate branches")
    @PreAuthorize("@permissionChecker.hasAny('BRANCH_LIST_ADMIN', 'BRANCH_LIST_DIRECTOR', 'BRANCH_DASHBOARD')")
    @GetMapping("/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<BranchResponse>>> getPage(
            @ModelAttribute PageRequestDTO pageRequest,
            @RequestParam(required = false) String status) {
        return successPage(branchService.getBranchPage(pageRequest, status));
    }

    @Operation(summary = "Get branch detail")
    @PreAuthorize("@permissionChecker.hasAny('BRANCH_LIST_ADMIN', 'BRANCH_DASHBOARD')")
    @GetMapping("/{id}")
    public ResponseEntity<TFUResponse<BranchResponse>> getById(@PathVariable Long id) {
        return success(branchService.getBranch(id));
    }

    @Operation(summary = "Update branch")
    @PreAuthorize("@permissionChecker.has('MANAGE_BRANCH_INFORMATION')")
    @PutMapping("/{id}")
    public ResponseEntity<TFUResponse<BranchResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBranchRequest request) {
        return success(branchService.updateBranch(id, request), "Branch updated successfully.");
    }

    @Operation(summary = "Send branch deactivation verification code")
    @PreAuthorize("@permissionChecker.has('MANAGE_BRANCH_INFORMATION')")
    @PostMapping("/{id}/suspend/send-code")
    public ResponseEntity<TFUResponse<String>> sendSuspendCode(
            @PathVariable Long id,
            @Valid @RequestBody SendBranchSuspendCodeRequest request) {
        branchService.sendBranchSuspendCode(id, request);
        return success("Verification code sent to your email.");
    }

    @Operation(summary = "Update branch status")
    @PreAuthorize("@permissionChecker.has('MANAGE_BRANCH_INFORMATION')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<TFUResponse<BranchResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBranchStatusRequest request) {
        return success(branchService.suspendBranch(id, request), "Branch status updated successfully.");
    }

    @Operation(summary = "Create branch manager")
    @PreAuthorize("@permissionChecker.has('MANAGE_BRANCH_INFORMATION')")
    @PostMapping("/{branchId}/manager")
    public ResponseEntity<TFUResponse<UserResponse>> createBranchManager(
            @PathVariable Long branchId,
            @Valid @RequestBody CreateBranchManagerRequest request) {
        UserResponse data = branchService.createBranchManager(branchId, request);
        TFUResponse<UserResponse> body = new TFUResponse<>(
                true, data, "Branch manager created successfully.", HttpStatus.CREATED.value(), null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Create inventory staff")
    @PreAuthorize("@permissionChecker.hasAny('MANAGE_BRANCH_INFORMATION', 'MANAGE_BRANCH_STAFF_INFO')")
    @PostMapping("/staff/inventory")
    public ResponseEntity<TFUResponse<UserResponse>> createInventoryStaff(
            @Valid @RequestBody CreateInventoryStaffRequest request) {
        UserResponse data = branchService.createInventoryStaff(request);
        TFUResponse<UserResponse> body = new TFUResponse<>(
                true, data, "Inventory staff created successfully.", HttpStatus.CREATED.value(), null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Assign existing staff to branch")
    @PreAuthorize("@permissionChecker.has('MANAGE_BRANCH_INFORMATION')")
    @PostMapping("/{branchId}/assign-staff")
    public ResponseEntity<TFUResponse<UserResponse>> assignStaff(
            @PathVariable Long branchId,
            @Valid @RequestBody AssignStaffRequest request) {
        UserResponse data = branchService.assignStaffToBranch(branchId, request);
        return success(data, "Staff assigned successfully.");
    }

    @Operation(summary = "Create cashier")
    @PreAuthorize("@permissionChecker.hasAny('MANAGE_BRANCH_INFORMATION', 'MANAGE_BRANCH_STAFF_INFO')")
    @PostMapping("/staff/cashier")
    public ResponseEntity<TFUResponse<UserResponse>> createCashier(
            @Valid @RequestBody CreateCashierRequest request) {
        UserResponse data = branchService.createCashier(request);
        TFUResponse<UserResponse> body = new TFUResponse<>(
                true, data, "Cashier created successfully.", HttpStatus.CREATED.value(), null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
