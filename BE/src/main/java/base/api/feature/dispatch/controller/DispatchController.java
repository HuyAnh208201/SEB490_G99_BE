package base.api.feature.dispatch.controller;

import base.api.feature.dispatch.dto.request.CreateDispatchOrderRequest;
import base.api.feature.dispatch.dto.request.UpdateDispatchStatusRequest;
import base.api.feature.dispatch.dto.response.DispatchApprovedRequestResponse;
import base.api.feature.dispatch.dto.response.DispatchOrderResponse;
import base.api.feature.dispatch.service.IDispatchService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.dto.PageResponseDTO;
import base.api.shared.enums.DispatchStatus;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@RestController
@RequestMapping("/api/dispatch-orders")
@Tag(name = "Dispatch Orders", description = "Warehouse dispatch planning & delivery tracking")
public class DispatchController extends BaseAPIController {

    @Autowired
    private IDispatchService dispatchService;

    @Operation(summary = "Approved requests ready for dispatch planning")
    @PreAuthorize("@permissionChecker.has('MANAGE_DISPATCH_ORDERS')")
    @GetMapping("/approved-requests")
    public ResponseEntity<TFUResponse<List<DispatchApprovedRequestResponse>>> getApprovedRequests() {
        return success(dispatchService.getApprovedRequests());
    }

    @Operation(summary = "Search, filter and paginate approved requests")
    @PreAuthorize("@permissionChecker.has('MANAGE_DISPATCH_ORDERS')")
    @GetMapping("/approved-requests/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<DispatchApprovedRequestResponse>>> getApprovedRequestPage(
            @ModelAttribute PageRequestDTO pageRequest) {
        return successPage(dispatchService.getApprovedRequestPage(pageRequest));
    }

    @Operation(summary = "Create a dispatch order for one approved request")
    @PreAuthorize("@permissionChecker.has('MANAGE_DISPATCH_ORDERS')")
    @PostMapping
    public ResponseEntity<TFUResponse<DispatchOrderResponse>> createDispatchOrder(
            @Valid @RequestBody CreateDispatchOrderRequest request
    ) {
        DispatchOrderResponse data = dispatchService.createDispatchOrder(request);
        TFUResponse<DispatchOrderResponse> body = new TFUResponse<>(
                true, data, "Dispatch order created successfully.", HttpStatus.CREATED.value(), null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "List dispatch orders")
    @PreAuthorize("@permissionChecker.has('MANAGE_DISPATCH_ORDERS')")
    @GetMapping
    public ResponseEntity<TFUResponse<List<DispatchOrderResponse>>> getDispatchOrders() {
        return success(dispatchService.getDispatchOrders());
    }

    @Operation(summary = "Search, filter and paginate dispatch orders")
    @PreAuthorize("@permissionChecker.has('MANAGE_DISPATCH_ORDERS')")
    @GetMapping("/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<DispatchOrderResponse>>> getDispatchOrderPage(
            @ModelAttribute PageRequestDTO pageRequest,
            @RequestParam(required = false) DispatchStatus status) {
        return successPage(dispatchService.getDispatchOrderPage(pageRequest, status));
    }

    @Operation(summary = "Get dispatch order detail")
    @PreAuthorize("@permissionChecker.has('MANAGE_DISPATCH_ORDERS')")
    @GetMapping("/{id}")
    public ResponseEntity<TFUResponse<DispatchOrderResponse>> getDispatchOrder(@PathVariable Long id) {
        return success(dispatchService.getDispatchOrder(id));
    }

    @Operation(summary = "Update dispatch order status")
    @PreAuthorize("@permissionChecker.has('MANAGE_DISPATCH_ORDERS')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<TFUResponse<DispatchOrderResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDispatchStatusRequest request
    ) {
        return success(dispatchService.updateStatus(id, request), "Dispatch status updated successfully.");
    }
}
