package base.api.feature.promotion.controller;

import base.api.feature.promotion.dto.request.ActivateCampaignRequest;
import base.api.feature.promotion.dto.request.CreateCampaignRequest;
import base.api.feature.promotion.dto.request.UpdateCampaignRequest;
import base.api.feature.promotion.dto.response.CampaignResponse;
import base.api.feature.promotion.dto.response.CampaignSummaryResponse;
import base.api.feature.promotion.service.ICampaignService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.dto.PageResponseDTO;
import base.api.shared.enums.CampaignStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/campaigns")
@Tag(name = "Campaigns", description = "Backend CRUD cho promotion campaign management")
public class PromotionController extends BaseAPIController {

    @Autowired
    private ICampaignService campaignService;

    @Operation(summary = "Create campaign")
    @PreAuthorize("@permissionChecker.has('PROMOTION_MANAGEMENT')")
    @PostMapping
    public ResponseEntity<TFUResponse<CampaignResponse>> create(@Valid @RequestBody CreateCampaignRequest request) {
        CampaignResponse data = campaignService.createCampaign(request);
        TFUResponse<CampaignResponse> body = new TFUResponse<>(
                true, data, "Promotion created successfully.", HttpStatus.CREATED.value(), null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Get campaign list")
    @PreAuthorize("@permissionChecker.has('PROMOTION_LIST')")
    @GetMapping
    public ResponseEntity<TFUResponse<List<CampaignSummaryResponse>>> getAll() {
        return success(campaignService.getAllCampaigns());
    }

    @Operation(summary = "Search, filter and paginate campaigns")
    @PreAuthorize("@permissionChecker.has('PROMOTION_LIST')")
    @GetMapping("/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<CampaignSummaryResponse>>> getPage(
            @ModelAttribute PageRequestDTO pageRequest,
            @RequestParam(required = false) CampaignStatus status,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String creatorTier) {
        return successPage(campaignService.getCampaignPage(pageRequest, status, branchId, creatorTier));
    }

    @Operation(summary = "Get campaign detail")
    @PreAuthorize("@permissionChecker.hasAny('PROMOTION_LIST', 'PROMOTION_DETAILS')")
    @GetMapping("/{id}")
    public ResponseEntity<TFUResponse<CampaignResponse>> getById(@PathVariable Long id) {
        return success(campaignService.getCampaign(id));
    }

    @Operation(summary = "Update campaign")
    @PreAuthorize("@permissionChecker.has('PROMOTION_MANAGEMENT')")
    @PutMapping("/{id}")
    public ResponseEntity<TFUResponse<CampaignResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCampaignRequest request) {
        return success(campaignService.updateCampaign(id, request), "Promotion updated successfully.");
    }

    @Operation(summary = "Delete campaign")
    @PreAuthorize("@permissionChecker.has('PROMOTION_MANAGEMENT')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        campaignService.deleteCampaign(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Activate campaign")
    @PreAuthorize("@permissionChecker.has('PROMOTION_MANAGEMENT')")
    @PatchMapping("/{id}/activate")
    public ResponseEntity<TFUResponse<CampaignResponse>> activate(
            @PathVariable Long id,
            @RequestBody(required = false) ActivateCampaignRequest request) {
        return success(
                campaignService.activateCampaign(id, request),
                "Promotion activated successfully.");
    }

    @Operation(summary = "Suspend campaign")
    @PreAuthorize("@permissionChecker.has('PROMOTION_MANAGEMENT')")
    @PatchMapping("/{id}/suspend")
    public ResponseEntity<TFUResponse<CampaignResponse>> suspend(@PathVariable Long id) {
        return success(campaignService.suspendCampaign(id), "Promotion suspended successfully.");
    }

    @Operation(summary = "Deactivate campaign for current branch")
    @PreAuthorize("@permissionChecker.has('PROMOTION_MANAGEMENT')")
    @PatchMapping("/{id}/deactivate-for-branch")
    public ResponseEntity<TFUResponse<CampaignResponse>> deactivateForBranch(@PathVariable Long id) {
        return success(
                campaignService.deactivateCampaignForBranch(id),
                "Promotion deactivated for branch successfully.");
    }

    @Operation(summary = "Activate campaign for current branch")
    @PreAuthorize("@permissionChecker.has('PROMOTION_MANAGEMENT')")
    @PatchMapping("/{id}/activate-for-branch")
    public ResponseEntity<TFUResponse<CampaignResponse>> activateForBranch(@PathVariable Long id) {
        return success(
                campaignService.activateCampaignForBranch(id),
                "Promotion activated for branch successfully.");
    }
}
