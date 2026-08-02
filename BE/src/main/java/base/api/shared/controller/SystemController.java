package base.api.shared.controller;

import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
@Tag(name = "System", description = "Shared system utilities")
public class SystemController extends BaseAPIController {

    @Operation(summary = "Server time (epoch milliseconds) for POS clock sync")
    @GetMapping("/time")
    public ResponseEntity<TFUResponse<Map<String, Long>>> serverTime() {
        return success(Map.of("epochMs", System.currentTimeMillis()));
    }
}
