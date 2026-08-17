package base.api.feature.purchaseorder.controller;

import base.api.feature.purchaseorder.dto.request.CreatePurchaseOrderRequest;
import base.api.feature.purchaseorder.dto.response.PurchaseOrderResponse;
import base.api.feature.purchaseorder.dto.response.PurchaseProductOptionResponse;
import base.api.feature.purchaseorder.dto.response.RecommendedPurchaseProductResponse;
import base.api.feature.purchaseorder.service.IPurchaseOrderService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.dto.PageResponseDTO;
import base.api.shared.enums.PurchaseOrderStatus;
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
@RequestMapping("/api/purchase-orders")
@Tag(name = "Purchase Orders", description = "Warehouse purchase orders to suppliers (replenish central stock)")
public class PurchaseOrderController extends BaseAPIController {

    @Autowired
    private IPurchaseOrderService purchaseOrderService;

    @Operation(summary = "Recommended products to purchase (short central stock)")
    @PreAuthorize("@permissionChecker.has('CHOOSE_EXTERNAL_SUPPLIER')")
    @GetMapping("/recommended-products")
    public ResponseEntity<TFUResponse<List<RecommendedPurchaseProductResponse>>> getRecommendedProducts() {
        return success(purchaseOrderService.getRecommendedProducts());
    }

    @Operation(summary = "Search products to add to a purchase order")
    @PreAuthorize("@permissionChecker.has('CHOOSE_EXTERNAL_SUPPLIER')")
    @GetMapping("/search-products")
    public ResponseEntity<TFUResponse<List<PurchaseProductOptionResponse>>> searchProducts(
            @RequestParam(value = "supplierId", required = false) Integer supplierId,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        return success(purchaseOrderService.searchProducts(supplierId, keyword));
    }

    @Operation(summary = "Record a received supplier delivery and update central stock")
    @PreAuthorize("@permissionChecker.has('CHOOSE_EXTERNAL_SUPPLIER')")
    @PostMapping
    public ResponseEntity<TFUResponse<PurchaseOrderResponse>> createOrder(
            @Valid @RequestBody CreatePurchaseOrderRequest request
    ) {
        PurchaseOrderResponse data = purchaseOrderService.createOrder(request);
        TFUResponse<PurchaseOrderResponse> body = new TFUResponse<>(
                true, data, "Supplier receipt recorded successfully.", HttpStatus.CREATED.value(), null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "List purchase orders")
    @PreAuthorize("@permissionChecker.hasAny('CHOOSE_EXTERNAL_SUPPLIER','VIEW_SUPPLIER_RECEIPTS_PRICES')")
    @GetMapping
    public ResponseEntity<TFUResponse<List<PurchaseOrderResponse>>> getOrders() {
        return success(purchaseOrderService.getOrders());
    }

    @Operation(summary = "Search, filter and paginate purchase orders")
    @PreAuthorize("@permissionChecker.hasAny('CHOOSE_EXTERNAL_SUPPLIER','VIEW_SUPPLIER_RECEIPTS_PRICES')")
    @GetMapping("/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<PurchaseOrderResponse>>> getOrderPage(
            @ModelAttribute PageRequestDTO pageRequest,
            @RequestParam(required = false) PurchaseOrderStatus status) {
        return successPage(purchaseOrderService.getOrderPage(pageRequest, status));
    }

    @Operation(summary = "Get purchase order detail")
    @PreAuthorize("@permissionChecker.hasAny('CHOOSE_EXTERNAL_SUPPLIER','VIEW_SUPPLIER_RECEIPTS_PRICES')")
    @GetMapping("/{id}")
    public ResponseEntity<TFUResponse<PurchaseOrderResponse>> getOrder(@PathVariable Long id) {
        return success(purchaseOrderService.getOrder(id));
    }

    @Operation(summary = "Receive a purchase order (add to central stock, release awaiting requests)")
    @PreAuthorize("@permissionChecker.has('CHOOSE_EXTERNAL_SUPPLIER')")
    @PatchMapping("/{id}/receive")
    public ResponseEntity<TFUResponse<PurchaseOrderResponse>> receiveOrder(@PathVariable Long id) {
        return success(purchaseOrderService.receiveOrder(id), "Purchase order received successfully.");
    }

    @Operation(summary = "Cancel a purchase order")
    @PreAuthorize("@permissionChecker.has('CHOOSE_EXTERNAL_SUPPLIER')")
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<TFUResponse<PurchaseOrderResponse>> cancelOrder(@PathVariable Long id) {
        return success(purchaseOrderService.cancelOrder(id), "Purchase order cancelled successfully.");
    }
}
