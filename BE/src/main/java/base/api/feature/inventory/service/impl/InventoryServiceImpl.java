package base.api.feature.inventory.service.impl;

import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.inventory.dto.response.BranchInventoryItemResponse;
import base.api.feature.inventory.dto.response.WarehouseInventoryItemResponse;
import base.api.feature.inventory.service.IInventoryService;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.shared.entity.BranchInventoryModel;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.UserModel;
import base.api.shared.entity.WarehouseInventoryModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryServiceImpl implements IInventoryService {

    @Autowired
    private WarehouseInventoryRepository warehouseInventoryRepository;

    @Autowired
    private BranchInventoryRepository branchInventoryRepository;

    @Autowired
    private IProductRepository productRepository;

    @Autowired
    private IBranchRepository branchRepository;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Override
    public List<WarehouseInventoryItemResponse> getWarehouseInventory() {
        assertCanViewCentralInventory();
        return mapWarehouseRows(warehouseInventoryRepository.findAll());
    }

    @Override
    public List<WarehouseInventoryItemResponse> getWarehouseLowStock() {
        assertCanViewCentralInventory();
        return mapWarehouseRows(warehouseInventoryRepository.findAll()).stream()
                .filter(WarehouseInventoryItemResponse::isLowStock)
                .toList();
    }

    @Override
    public List<BranchInventoryItemResponse> getBranchInventory(Long branchId) {
        assertCanViewBranchInventory(branchId);
        BranchModel branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NotFoundException("Branch not found."));
        Map<Integer, ProductModel> productsById = productRepository.findAll().stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity(), (a, b) -> a));

        List<BranchInventoryItemResponse> rows = new ArrayList<>();
        for (BranchInventoryModel row : branchInventoryRepository.findByBranchId(branchId)) {
            ProductModel product = productsById.get(row.getProductId());
            BranchInventoryItemResponse response = new BranchInventoryItemResponse();
            response.setInventoryId(row.getId());
            response.setBranchId(branchId);
            response.setBranchName(branch.getName());
            response.setProductId(row.getProductId());
            response.setProductCode(product == null ? null : product.getCode());
            response.setProductName(product == null ? null : product.getName());
            response.setUnit(product == null ? null : product.getUnit());
            response.setQuantity(safeQty(row.getCurrentStock()));
            rows.add(response);
        }

        rows.sort(Comparator.comparing(
                BranchInventoryItemResponse::getProductName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return rows;
    }

    private List<WarehouseInventoryItemResponse> mapWarehouseRows(List<WarehouseInventoryModel> inventoryRows) {
        Map<Integer, ProductModel> productsById = productRepository.findAll().stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity(), (a, b) -> a));

        List<WarehouseInventoryItemResponse> rows = new ArrayList<>();
        for (WarehouseInventoryModel row : inventoryRows) {
            ProductModel product = productsById.get(row.getProductId());
            int quantity = safeQty(row.getQuantity());
            int reorderPoint = safeQty(row.getReorderPoint());

            WarehouseInventoryItemResponse response = new WarehouseInventoryItemResponse();
            response.setInventoryId(row.getId());
            response.setProductId(row.getProductId());
            response.setProductCode(product == null ? null : product.getCode());
            response.setProductName(product == null ? null : product.getName());
            response.setUnit(product == null ? null : product.getUnit());
            response.setQuantity(quantity);
            response.setReorderPoint(reorderPoint);
            response.setLowStock(reorderPoint > 0 && quantity <= reorderPoint);
            rows.add(response);
        }

        rows.sort(Comparator.comparing(
                WarehouseInventoryItemResponse::getProductName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return rows;
    }

    private void assertCanViewCentralInventory() {
        UserRole role = currentUserProvider.getCurrentUserRole();
        if (role == UserRole.ADMIN
                || role == UserRole.DIRECTOR
                || role == UserRole.WAREHOUSE_MANAGER) {
            return;
        }
        throw new ForbiddenException("Access denied.");
    }

    private void assertCanViewBranchInventory(Long branchId) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole role = currentUserProvider.getCurrentUserRole();
        if (role == UserRole.ADMIN || role == UserRole.DIRECTOR || role == UserRole.WAREHOUSE_MANAGER) {
            return;
        }
        if ((role == UserRole.BRANCH_MANAGER || role == UserRole.INVENTORY_STAFF)
                && Objects.equals(currentUser.getBranchId(), branchId)) {
            return;
        }
        throw new ForbiddenException("Access denied.");
    }

    private int safeQty(Integer value) {
        return value == null ? 0 : value;
    }
}
