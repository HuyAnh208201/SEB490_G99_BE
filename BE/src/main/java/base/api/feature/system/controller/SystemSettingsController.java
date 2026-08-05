package base.api.feature.system.controller;

import base.api.feature.system.dto.request.UpdateMembershipTierRequest;
import base.api.feature.system.dto.response.MembershipTierResponse;
import base.api.feature.system.service.IMembershipTierService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@Tag(name = "System", description = "System settings / membership tiers")
public class SystemSettingsController extends BaseAPIController {

    @Autowired
    private IMembershipTierService membershipTierService;

    @Operation(summary = "System settings summary")
    @PreAuthorize("@permissionChecker.has('SYSTEM_SETTINGS_MASTER_DATA')")
    @GetMapping("settings")
    public ResponseEntity<TFUResponse<Map<String, Object>>> settings() {
        return success(Map.of(
                "module", "system",
                "screen", "System Settings",
                "tiersEditable", true,
                "pointRatesNote", "Point earn/redeem rates are configured on the server."
        ));
    }

    @Operation(summary = "List membership tiers")
    @PreAuthorize("@permissionChecker.has('SYSTEM_SETTINGS_MASTER_DATA')")
    @GetMapping("membership-tiers")
    public ResponseEntity<TFUResponse<List<MembershipTierResponse>>> listTiers() {
        return success(membershipTierService.listTiers());
    }

    @Operation(summary = "Update a membership tier (code is immutable)")
    @PreAuthorize("@permissionChecker.has('SYSTEM_SETTINGS_MASTER_DATA')")
    @PutMapping("membership-tiers/{id}")
    public ResponseEntity<TFUResponse<MembershipTierResponse>> updateTier(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMembershipTierRequest request) {
        return success(membershipTierService.updateTier(id, request), "Membership tier updated.");
    }
}
