package base.api.feature.admin.controller;

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
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Màn hình Admin Dashboard")
public class AdminDashboardController extends StubModuleController {

    @Operation(summary = "Admin Dashboard")
    @PreAuthorize("@permissionChecker.has('ADMIN_DASHBOARD')")
    @GetMapping("dashboard")
    public ResponseEntity<TFUResponse<Map<String, String>>> dashboard() {
        return stub("admin", "Admin Dashboard");
    }
}
