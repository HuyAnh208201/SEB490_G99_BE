package base.api.feature.inventory.controller;

import base.api.feature.inventory.dto.request.UpdateBranchReorderPointRequest;
import base.api.feature.inventory.dto.response.BranchInventoryItemResponse;
import base.api.feature.inventory.dto.response.WarehouseInventoryItemResponse;
import base.api.feature.inventory.service.IInventoryService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "Central warehouse and branch stock levels")
public class InventoryController extends BaseAPIController {

    @Autowired
    private IInventoryService inventoryService;

    @Operation(summary = "List central warehouse inventory")
    @PreAuthorize("@permissionChecker.has('VIEW_CENTRAL_INVENTORY')")
    @GetMapping("/warehouse")
    public ResponseEntity<TFUResponse<List<WarehouseInventoryItemResponse>>> getWarehouseInventory() {
        return success(inventoryService.getWarehouseInventory());
    }

    @Operation(summary = "Search, filter and paginate central warehouse inventory")
    @PreAuthorize("@permissionChecker.has('VIEW_CENTRAL_INVENTORY')")
    @GetMapping("/warehouse/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<WarehouseInventoryItemResponse>>> getWarehouseInventoryPage(
            @ModelAttribute PageRequestDTO pageRequest,
            @RequestParam(defaultValue = "false") boolean lowStockOnly) {
        return successPage(inventoryService.getWarehouseInventoryPage(pageRequest, lowStockOnly));
    }

    @Operation(summary = "List central warehouse low-stock items")
    @PreAuthorize("@permissionChecker.has('VIEW_CENTRAL_INVENTORY')")
    @GetMapping("/warehouse/low-stock")
    public ResponseEntity<TFUResponse<List<WarehouseInventoryItemResponse>>> getWarehouseLowStock() {
        return success(inventoryService.getWarehouseLowStock());
    }

    @Operation(summary = "List branch inventory")
    @PreAuthorize("@permissionChecker.hasAny('VIEW_BRANCH_INVENTORY', 'SUPPLY_IMPORT_RECEIPT_APPROVE', 'BRANCH_DASHBOARD')")
    @GetMapping("/branches/{branchId}")
    public ResponseEntity<TFUResponse<List<BranchInventoryItemResponse>>> getBranchInventory(
            @PathVariable Long branchId) {
        return success(inventoryService.getBranchInventory(branchId));
    }

    @Operation(summary = "Search and paginate branch inventory")
    @PreAuthorize("@permissionChecker.hasAny('VIEW_BRANCH_INVENTORY', 'SUPPLY_IMPORT_RECEIPT_APPROVE', 'BRANCH_DASHBOARD')")
    @GetMapping("/branches/{branchId}/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<BranchInventoryItemResponse>>> getBranchInventoryPage(
            @PathVariable Long branchId,
            @ModelAttribute PageRequestDTO pageRequest) {
        return successPage(inventoryService.getBranchInventoryPage(branchId, pageRequest));
    }

    @Operation(summary = "Update branch reorder point for a product (BM own branch)")
    @PreAuthorize("@permissionChecker.hasAny('VIEW_BRANCH_INVENTORY', 'CREATE_IMPORT_REQUEST')")
    @PatchMapping("/branches/{branchId}/products/{productId}/reorder-point")
    public ResponseEntity<TFUResponse<BranchInventoryItemResponse>> updateBranchReorderPoint(
            @PathVariable Long branchId,
            @PathVariable Integer productId,
            @Valid @RequestBody UpdateBranchReorderPointRequest request) {
        return success(inventoryService.updateBranchReorderPoint(
                branchId, productId, request.getReorderPoint()));
    }
}
