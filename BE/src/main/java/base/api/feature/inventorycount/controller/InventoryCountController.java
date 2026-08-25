package base.api.feature.inventorycount.controller;

import base.api.feature.inventorycount.dto.request.SubmitInventoryCountRequest;
import base.api.feature.inventorycount.dto.response.InventoryCountSessionResponse;
import base.api.feature.inventorycount.dto.response.InventoryCountSheetResponse;
import base.api.feature.inventorycount.service.IInventoryCountService;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/inventory-counts")
@Tag(name = "Inventory Count", description = "Inventory staff: physical stock counting & stock updates")
public class InventoryCountController extends BaseAPIController {

    @Autowired
    private IInventoryCountService inventoryCountService;

    @Operation(summary = "Count sheet: paged products with system quantity for the staff's branch")
    @PreAuthorize("@permissionChecker.has('INVENTORY_COUNT')")
    @GetMapping("/sheet")
    public ResponseEntity<TFUResponse<InventoryCountSheetResponse>> getCountSheet(
            @ModelAttribute PageRequestDTO pageRequest,
            @RequestParam(required = false) Integer categoryId) {
        return success(inventoryCountService.getCountSheet(pageRequest, categoryId));
    }

    @Operation(summary = "Submit an inventory count session")
    @PreAuthorize("@permissionChecker.has('INVENTORY_COUNT')")
    @PostMapping
    public ResponseEntity<TFUResponse<InventoryCountSessionResponse>> submitCount(
            @Valid @RequestBody SubmitInventoryCountRequest request
    ) {
        return success(inventoryCountService.submitCount(request), "Inventory count submitted successfully.");
    }

    @Operation(summary = "Inventory count history for the staff's branch")
    @PreAuthorize("@permissionChecker.has('INVENTORY_COUNT')")
    @GetMapping
    public ResponseEntity<TFUResponse<List<InventoryCountSessionResponse>>> getHistory() {
        return success(inventoryCountService.getHistory());
    }

    @Operation(summary = "Search, filter and paginate inventory count history")
    @PreAuthorize("@permissionChecker.has('INVENTORY_COUNT')")
    @GetMapping("/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<InventoryCountSessionResponse>>> getHistoryPage(
            @ModelAttribute PageRequestDTO pageRequest,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String discrepancy,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return successPage(inventoryCountService.getHistoryPage(pageRequest, status, discrepancy, from, to));
    }

    @Operation(summary = "Inventory count session detail")
    @PreAuthorize("@permissionChecker.has('INVENTORY_COUNT')")
    @GetMapping("/{id}")
    public ResponseEntity<TFUResponse<InventoryCountSessionResponse>> getSession(@PathVariable Long id) {
        return success(inventoryCountService.getSession(id));
    }
}
