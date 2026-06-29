package base.api.feature.system.controller;

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
@RequestMapping("/api/system")
@Tag(name = "System", description = "Cấu hình hệ thống / Master Data")
public class SystemSettingsController extends StubModuleController {

    @Operation(summary = "System Settings Master Data")
    @PreAuthorize("@permissionChecker.has('SYSTEM_SETTINGS_MASTER_DATA')")
    @GetMapping("settings")
    public ResponseEntity<TFUResponse<Map<String, String>>> settings() {
        return stub("system", "System Settings Master Data");
    }
}
