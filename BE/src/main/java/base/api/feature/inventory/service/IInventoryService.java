package base.api.feature.inventory.service;

import base.api.feature.inventory.dto.response.BranchInventoryItemResponse;
import base.api.feature.inventory.dto.response.WarehouseInventoryItemResponse;
import base.api.shared.dto.PageRequestDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface IInventoryService {

    List<WarehouseInventoryItemResponse> getWarehouseInventory();

    List<WarehouseInventoryItemResponse> getWarehouseLowStock();

    List<BranchInventoryItemResponse> getBranchInventory(Long branchId);

    Page<WarehouseInventoryItemResponse> getWarehouseInventoryPage(
            PageRequestDTO pageRequest, boolean lowStockOnly);

    Page<BranchInventoryItemResponse> getBranchInventoryPage(
            Long branchId, PageRequestDTO pageRequest);

    BranchInventoryItemResponse updateBranchReorderPoint(
            Long branchId, Integer productId, Integer reorderPoint);
}
