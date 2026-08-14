package base.api.feature.branchreceiving.controller;

import base.api.feature.branchreceiving.dto.request.ReceiveShipmentRequest;
import base.api.feature.branchreceiving.dto.response.ReceiveShipmentDetailResponse;
import base.api.feature.branchreceiving.dto.response.ReceivingHistoryResponse;
import base.api.feature.branchreceiving.dto.response.ReceivingOrderResponse;
import base.api.feature.branchreceiving.dto.response.ReceivingReceiptDetailResponse;
import base.api.feature.branchreceiving.dto.response.SupplementalRequestResponse;
import base.api.feature.branchreceiving.service.IBranchReceivingService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.dto.PageResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/branch-receiving")
@Tag(name = "Branch Receiving", description = "Inventory staff: order tracking, receive shipment, receiving history")
public class BranchReceivingController extends BaseAPIController {

    @Autowired
    private IBranchReceivingService branchReceivingService;

    @Operation(summary = "Incoming dispatch orders for the staff's branch (Order Tracking)")
    @PreAuthorize("@permissionChecker.has('RECEIVE_SHIPMENT')")
    @GetMapping("/orders")
    public ResponseEntity<TFUResponse<List<ReceivingOrderResponse>>> getIncomingOrders() {
        return success(branchReceivingService.getIncomingOrders());
    }

    @Operation(summary = "Paginated incoming dispatch orders for the staff's branch")
    @PreAuthorize("@permissionChecker.has('RECEIVE_SHIPMENT')")
    @GetMapping("/orders/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<ReceivingOrderResponse>>> getIncomingOrderPage(
            PageRequestDTO pageRequest,
            @RequestParam(required = false) String status
    ) {
        return successPage(branchReceivingService.getIncomingOrderPage(pageRequest, status));
    }

    @Operation(summary = "Shipment detail to receive (Receive Shipment)")
    @PreAuthorize("@permissionChecker.has('RECEIVE_SHIPMENT')")
    @GetMapping("/orders/{dispatchOrderId}/requests/{requestId}")
    public ResponseEntity<TFUResponse<ReceiveShipmentDetailResponse>> getShipmentDetail(
            @PathVariable Long dispatchOrderId,
            @PathVariable Long requestId
    ) {
        return success(branchReceivingService.getShipmentDetail(dispatchOrderId, requestId));
    }

    @Operation(summary = "Confirm actual received quantities (nhập kho thực tế)")
    @PreAuthorize("@permissionChecker.has('RECEIVE_SHIPMENT')")
    @PostMapping("/orders/{dispatchOrderId}/requests/{requestId}/receive")
    public ResponseEntity<TFUResponse<ReceivingHistoryResponse>> receiveShipment(
            @PathVariable Long dispatchOrderId,
            @PathVariable Long requestId,
            @Valid @RequestBody ReceiveShipmentRequest request
    ) {
        return success(
                branchReceivingService.receiveShipment(dispatchOrderId, requestId, request),
                "Shipment received and branch stock updated.");
    }

    @Operation(summary = "Receiving history for the branch")
    @PreAuthorize("@permissionChecker.hasAny('RECEIVE_SHIPMENT', 'SUPPLY_IMPORT_RECEIPT_APPROVE')")
    @GetMapping("/receipts")
    public ResponseEntity<TFUResponse<List<ReceivingHistoryResponse>>> getReceivingHistory() {
        return success(branchReceivingService.getReceivingHistory());
    }

    @Operation(summary = "Paginated receiving history for the branch")
    @PreAuthorize("@permissionChecker.hasAny('RECEIVE_SHIPMENT', 'SUPPLY_IMPORT_RECEIPT_APPROVE')")
    @GetMapping("/receipts/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<ReceivingHistoryResponse>>> getReceivingHistoryPage(
            PageRequestDTO pageRequest,
            @RequestParam(required = false) String status
    ) {
        return successPage(branchReceivingService.getReceivingHistoryPage(pageRequest, status));
    }

    @Operation(summary = "Receiving receipt detail")
    @PreAuthorize("@permissionChecker.hasAny('RECEIVE_SHIPMENT', 'SUPPLY_IMPORT_RECEIPT_APPROVE')")
    @GetMapping("/receipts/{receiptId}")
    public ResponseEntity<TFUResponse<ReceivingReceiptDetailResponse>> getReceiptDetail(
            @PathVariable Long receiptId
    ) {
        return success(branchReceivingService.getReceiptDetail(receiptId));
    }

    @Operation(summary = "Create a draft request for quantities missing from a receipt")
    @PreAuthorize("@permissionChecker.has('CREATE_IMPORT_REQUEST')")
    @PostMapping("/receipts/{receiptId}/supplement")
    public ResponseEntity<TFUResponse<SupplementalRequestResponse>> createSupplementalRequest(
            @PathVariable Long receiptId
    ) {
        return success(
                branchReceivingService.createSupplementalRequest(receiptId),
                "Supplemental purchase request draft is ready.");
    }

}
