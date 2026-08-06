package base.api.feature.admin.controller;

import base.api.feature.admin.dto.response.AdminDashboardResponse;
import base.api.feature.admin.service.IAdminDashboardService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Màn hình Admin Dashboard")
public class AdminDashboardController extends BaseAPIController {

    @Autowired
    private IAdminDashboardService adminDashboardService;

    @Operation(summary = "Admin Dashboard — system access & branch coverage")
    @PreAuthorize("@permissionChecker.has('ADMIN_DASHBOARD')")
    @GetMapping("dashboard")
    public ResponseEntity<TFUResponse<AdminDashboardResponse>> dashboard() {
        return success(adminDashboardService.getDashboard());
    }
}
