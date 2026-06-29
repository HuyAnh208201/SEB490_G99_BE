package base.api.feature.promotion.controller;

import base.api.shared.base.StubModuleController;
import base.api.shared.dto.TFUResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/promotions")
@Tag(name = "Promotion", description = "Khuyến mãi")
public class PromotionController extends StubModuleController {

    @Operation(summary = "Promotion List")
    @PreAuthorize("@permissionChecker.has('PROMOTION_LIST')")
    @GetMapping
    public ResponseEntity<TFUResponse<Map<String, String>>> list() {
        return stub("promotion", "Promotion List Screen");
    }

    @Operation(summary = "Promotion Details")
    @PreAuthorize("@permissionChecker.has('PROMOTION_DETAILS')")
    @GetMapping("{id}")
    public ResponseEntity<TFUResponse<Map<String, String>>> details(@PathVariable Long id) {
        return stub("promotion", "Promotion Details Screen");
    }
}
