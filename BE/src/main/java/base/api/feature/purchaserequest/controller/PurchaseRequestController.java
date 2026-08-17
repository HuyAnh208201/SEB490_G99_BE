package base.api.feature.purchaserequest.controller;

import base.api.feature.purchaserequest.dto.request.ApprovePurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.request.CreatePurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.request.ReceiveGoodsRequest;
import base.api.feature.purchaserequest.dto.request.RejectPurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.request.SaveDraftRequest;
import base.api.feature.purchaserequest.dto.request.SubmitPurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.response.ConsolidatedBranchResponse;
import base.api.feature.purchaserequest.dto.response.ProductSearchResponse;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestBranchResponse;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestResponse;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestSummaryResponse;
import base.api.feature.purchaserequest.dto.response.RecommendedProductResponse;
import base.api.feature.purchaserequest.service.IPurchaseRequestService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.dto.PageResponseDTO;
import base.api.shared.dto.TFUResponse;
import base.api.shared.enums.PurchaseRequestStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-requests")
@Tag(name = "Purchase Requests", description = "Purchase request management")
public class PurchaseRequestController extends BaseAPIController {

    @Autowired
    private IPurchaseRequestService purchaseRequestService;

    @Operation(summary = "Create purchase request draft")
    @PreAuthorize("@permissionChecker.has('CREATE_IMPORT_REQUEST')")
    @PostMapping("/draft")
    public ResponseEntity<TFUResponse<PurchaseRequestResponse>> createDraft(
            @Valid @RequestBody CreatePurchaseRequestRequest request
    ) {
        PurchaseRequestResponse data = purchaseRequestService.createDraft(request);
        TFUResponse<PurchaseRequestResponse> body = new TFUResponse<>(
                true, data, "Purchase request draft created successfully.", HttpStatus.CREATED.value(), null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Save purchase request draft")
    @PreAuthorize("@permissionChecker.has('CREATE_IMPORT_REQUEST')")
    @PutMapping("/{id}/draft")
    public ResponseEntity<TFUResponse<PurchaseRequestResponse>> saveDraft(
            @PathVariable Long id,
            @Valid @RequestBody SaveDraftRequest request
    ) {
        return success(purchaseRequestService.saveDraft(id, request), "Purchase request draft saved successfully.");
    }

    @Operation(summary = "Submit purchase request")
    @PreAuthorize("@permissionChecker.has('CREATE_IMPORT_REQUEST')")
    @PatchMapping("/{id}/submit")
    public ResponseEntity<TFUResponse<PurchaseRequestResponse>> submitRequest(
            @PathVariable Long id,
            @RequestBody(required = false) SubmitPurchaseRequestRequest request
    ) {
        SubmitPurchaseRequestRequest safeRequest = request == null ? new SubmitPurchaseRequestRequest() : request;
        return success(purchaseRequestService.submitRequest(id, safeRequest), "Purchase request submitted successfully.");
    }

    @Operation(summary = "Cancel purchase request")
    @PreAuthorize("@permissionChecker.has('CREATE_IMPORT_REQUEST')")
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<TFUResponse<PurchaseRequestResponse>> cancelRequest(@PathVariable Long id) {
        return success(purchaseRequestService.cancelRequest(id), "Purchase request cancelled successfully.");
    }

    @Operation(summary = "Approve purchase request")
    @PreAuthorize("@permissionChecker.has('APPROVE_IMPORT_REQUEST')")
    @PatchMapping("/{id}/approve")
    public ResponseEntity<TFUResponse<PurchaseRequestResponse>> approveRequest(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ApprovePurchaseRequestRequest request
    ) {
        ApprovePurchaseRequestRequest safeRequest = request == null ? new ApprovePurchaseRequestRequest() : request;
        return success(purchaseRequestService.approveRequest(id, safeRequest), "Purchase request approved successfully.");
    }

    @Operation(summary = "Reject purchase request (removed)")
    @PreAuthorize("@permissionChecker.has('APPROVE_IMPORT_REQUEST')")
    @PatchMapping("/{id}/reject")
    public ResponseEntity<TFUResponse<PurchaseRequestResponse>> rejectRequest(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) RejectPurchaseRequestRequest request
    ) {
        return success(purchaseRequestService.rejectRequest(id, request), "Rejecting purchase requests is not supported.");
    }

    @Operation(summary = "Receive purchase request goods")
    @PreAuthorize("@permissionChecker.has('SUPPLY_IMPORT_RECEIPT_APPROVE')")
    @PostMapping("/{id}/receive")
    public ResponseEntity<TFUResponse<PurchaseRequestResponse>> receiveGoods(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ReceiveGoodsRequest request
    ) {
        ReceiveGoodsRequest safeRequest = request == null ? new ReceiveGoodsRequest() : request;
        return success(purchaseRequestService.receiveGoods(id, safeRequest), "Purchase request received successfully.");
    }

    @Operation(summary = "Get purchase request history")
    @PreAuthorize("@permissionChecker.hasAny('CREATE_IMPORT_REQUEST', 'MANAGE_BRANCH_IMPORT_REQUESTS', 'APPROVE_IMPORT_REQUEST', 'SUPPLY_IMPORT_RECEIPT_APPROVE')")
    @GetMapping
    public ResponseEntity<TFUResponse<base.api.shared.dto.PageResponseDTO<PurchaseRequestSummaryResponse>>> getRequestHistory(
            @ModelAttribute PageRequestDTO pageRequest,
            @RequestParam(required = false) PurchaseRequestStatus status,
            @RequestParam(required = false) Long branchId
    ) {
        Page<PurchaseRequestSummaryResponse> page = purchaseRequestService.getRequestHistory(
                pageRequest, status, branchId);
        return successPage(page);
    }

    @Operation(summary = "List branches that have warehouse-visible purchase requests (for filters)")
    @PreAuthorize("@permissionChecker.hasAny('MANAGE_BRANCH_IMPORT_REQUESTS', 'APPROVE_IMPORT_REQUEST')")
    @GetMapping("/branches")
    public ResponseEntity<TFUResponse<List<PurchaseRequestBranchResponse>>> getWarehouseFilterBranches() {
        return success(purchaseRequestService.getWarehouseFilterBranches());
    }

    @Operation(summary = "Get purchase request detail")
    @PreAuthorize("@permissionChecker.hasAny('CREATE_IMPORT_REQUEST', 'MANAGE_BRANCH_IMPORT_REQUESTS', 'APPROVE_IMPORT_REQUEST', 'SUPPLY_IMPORT_RECEIPT_APPROVE')")
    @GetMapping("/{id}")
    public ResponseEntity<TFUResponse<PurchaseRequestResponse>> getRequest(@PathVariable Long id) {
        return success(purchaseRequestService.getRequest(id));
    }

    @Operation(summary = "Get recommended products")
    @PreAuthorize("@permissionChecker.has('CREATE_IMPORT_REQUEST')")
    @GetMapping("/recommended-products")
    public ResponseEntity<TFUResponse<List<RecommendedProductResponse>>> getRecommendedProducts() {
        return success(purchaseRequestService.getRecommendedProducts());
    }

    @Operation(summary = "Search active products")
    @PreAuthorize("@permissionChecker.has('CREATE_IMPORT_REQUEST')")
    @GetMapping("/search-products")
    public ResponseEntity<TFUResponse<base.api.shared.dto.PageResponseDTO<ProductSearchResponse>>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Boolean lowStockOnly,
            @RequestParam(required = false) String stockSort,
            @ModelAttribute PageRequestDTO pageRequest
    ) {
        Page<ProductSearchResponse> page = purchaseRequestService.searchProducts(
                keyword, pageRequest, categoryId, lowStockOnly, stockSort);
        return successPage(page);
    }

    @Operation(summary = "Get consolidated purchase requests (gom đơn theo địa chỉ chi nhánh & category)")
    @PreAuthorize("@permissionChecker.hasAny('APPROVE_IMPORT_REQUEST', 'MANAGE_BRANCH_IMPORT_REQUESTS')")
    @GetMapping("/consolidated")
    public ResponseEntity<TFUResponse<List<ConsolidatedBranchResponse>>> getConsolidatedRequests() {
        return success(purchaseRequestService.getConsolidatedRequests());
    }

    @Operation(summary = "Get paginated consolidated purchase requests")
    @PreAuthorize("@permissionChecker.hasAny('APPROVE_IMPORT_REQUEST', 'MANAGE_BRANCH_IMPORT_REQUESTS')")
    @GetMapping("/consolidated/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<ConsolidatedBranchResponse>>> getConsolidatedRequestPage(
            @ModelAttribute PageRequestDTO pageRequest
    ) {
        return successPage(purchaseRequestService.getConsolidatedRequestPage(pageRequest));
    }
}
