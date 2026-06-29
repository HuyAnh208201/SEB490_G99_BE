package base.api.feature.branch.controller;

import base.api.shared.base.StubModuleController;
import base.api.shared.dto.TFUResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/branches")
@Tag(name = "Branch (Admin/Director)", description = "Quản lý chi nhánh — góc Admin/Director")
public class BranchController extends StubModuleController {

    @Operation(summary = "Branch List (Admin)")
    @PreAuthorize("@permissionChecker.has('BRANCH_LIST_ADMIN')")
    @GetMapping
    public ResponseEntity<TFUResponse<Map<String, String>>> listAdmin() {
        return stub("branch", "Branch List Screen (Admin)");
    }

    @Operation(summary = "Manage Branch Information")
    @PreAuthorize("@permissionChecker.has('MANAGE_BRANCH_INFORMATION')")
    @GetMapping("manage")
    public ResponseEntity<TFUResponse<Map<String, String>>> manage() {
        return stub("branch", "Manage Branch Information Screen");
    }
}
