package base.api.feature.inventory.service;

import base.api.feature.inventory.dto.response.BranchInventoryItemResponse;
import base.api.feature.inventory.dto.response.WarehouseInventoryItemResponse;

import java.util.List;

public interface IInventoryService {

    List<WarehouseInventoryItemResponse> getWarehouseInventory();

    List<WarehouseInventoryItemResponse> getWarehouseLowStock();

    List<BranchInventoryItemResponse> getBranchInventory(Long branchId);
}
