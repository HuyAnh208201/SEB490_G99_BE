package base.api.feature.warehouse.controller;

import base.api.feature.warehouse.dto.response.WarehouseDashboardResponse;
import base.api.feature.warehouse.service.IWarehouseDashboardService;
import base.api.shared.base.StubModuleController;
import base.api.shared.dto.TFUResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDate;

import java.util.Map;

@RestController
@RequestMapping("/api/warehouse")
@Tag(name = "Warehouse", description = "Quản lý kho tổng")
public class WarehouseController extends StubModuleController {

    @Autowired
    private IWarehouseDashboardService warehouseDashboardService;

    @Operation(summary = "Warehouse Dashboard")
    @PreAuthorize("@permissionChecker.hasAny('WAREHOUSE_DASHBOARD','VIEW_SUPPLIER_RECEIPTS_PRICES')")
    @GetMapping("dashboard")
    public ResponseEntity<TFUResponse<WarehouseDashboardResponse>> dashboard(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return success(warehouseDashboardService.getDashboard(from, to));
    }

    @Operation(summary = "View Central Inventory")
    @PreAuthorize("@permissionChecker.has('VIEW_CENTRAL_INVENTORY')")
    @GetMapping("inventory")
    public ResponseEntity<TFUResponse<Map<String, String>>> inventory() {
        return stub("warehouse", "View Central Inventory");
    }

    @Operation(summary = "Manage Branch Import Requests")
    @PreAuthorize("@permissionChecker.has('MANAGE_BRANCH_IMPORT_REQUESTS')")
    @GetMapping("import-requests")
    public ResponseEntity<TFUResponse<Map<String, String>>> importRequests() {
        return stub("warehouse", "Manage Branch Import Requests");
    }

    @Operation(summary = "Choose External Supplier")
    @PreAuthorize("@permissionChecker.has('CHOOSE_EXTERNAL_SUPPLIER')")
    @PostMapping("suppliers/choose")
    public ResponseEntity<TFUResponse<Map<String, String>>> chooseSupplier() {
        return stub("warehouse", "Choose External Supplier");
    }

    @Operation(summary = "Manage Dispatch Orders")
    @PreAuthorize("@permissionChecker.has('MANAGE_DISPATCH_ORDERS')")
    @GetMapping("dispatch-orders")
    public ResponseEntity<TFUResponse<Map<String, String>>> dispatchOrders() {
        return stub("warehouse", "Manage Dispatch Orders");
    }
}
