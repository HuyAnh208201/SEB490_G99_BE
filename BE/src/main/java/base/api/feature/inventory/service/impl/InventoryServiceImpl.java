package base.api.feature.inventory.service.impl;

import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.inventory.dto.response.BranchInventoryItemResponse;
import base.api.feature.inventory.dto.response.WarehouseInventoryItemResponse;
import base.api.feature.inventory.service.IInventoryService;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.shared.entity.BranchInventoryModel;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.UserModel;
import base.api.shared.entity.WarehouseInventoryModel;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    @Autowired
    private ProductPackagingService productPackagingService;

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

        Set<Integer> productIds = branchInventoryRepository.findByBranchId(branchId).stream()
                .map(BranchInventoryModel::getProductId)
                .collect(Collectors.toSet());
        Map<Integer, ProductModel> productsById = loadProductsByIds(productIds);

        List<BranchInventoryItemResponse> rows = new ArrayList<>();
        for (BranchInventoryModel row : branchInventoryRepository.findByBranchId(branchId)) {
            rows.add(toBranchResponse(row, branch, productsById.get(row.getProductId())));
        }

        rows.sort(Comparator.comparing(
                BranchInventoryItemResponse::getProductName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return rows;
    }

    @Override
    public Page<WarehouseInventoryItemResponse> getWarehouseInventoryPage(
            PageRequestDTO pageRequest,
            boolean lowStockOnly
    ) {
        assertCanViewCentralInventory();
        PageRequestDTO query = pageRequest == null ? new PageRequestDTO() : pageRequest;
        Specification<WarehouseInventoryModel> specification = (root, ignored, cb) -> cb.conjunction();
        Set<Integer> matchingProductIds = findMatchingProductIds(query.normalizedSearch());
        if (query.normalizedSearch() != null) {
            specification = matchingProductIds.isEmpty()
                    ? specification.and((root, ignored, cb) -> cb.disjunction())
                    : specification.and((root, ignored, cb) -> root.get("productId").in(matchingProductIds));
        }
        if (lowStockOnly) {
            specification = specification.and((root, ignored, cb) -> cb.and(
                    cb.greaterThan(root.get("reorderPoint"), 0),
                    cb.lessThanOrEqualTo(root.get("quantity"), root.get("reorderPoint"))));
        }
        Page<WarehouseInventoryModel> page = warehouseInventoryRepository.findAll(
                specification,
                query.toPageable(
                        "id",
                        Sort.Direction.ASC,
                        Set.of("id", "productId", "quantity", "reorderPoint")));
        return new PageImpl<>(mapWarehouseRows(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    @Override
    public Page<BranchInventoryItemResponse> getBranchInventoryPage(
            Long branchId,
            PageRequestDTO pageRequest
    ) {
        assertCanViewBranchInventory(branchId);
        BranchModel branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NotFoundException("Branch not found."));
        PageRequestDTO query = pageRequest == null ? new PageRequestDTO() : pageRequest;
        Specification<BranchInventoryModel> specification = (root, ignored, cb) ->
                cb.equal(root.get("branchId"), branchId);
        Set<Integer> matchingProductIds = findMatchingProductIds(query.normalizedSearch());
        if (query.normalizedSearch() != null) {
            specification = matchingProductIds.isEmpty()
                    ? specification.and((root, ignored, cb) -> cb.disjunction())
                    : specification.and((root, ignored, cb) -> root.get("productId").in(matchingProductIds));
        }
        Page<BranchInventoryModel> page = branchInventoryRepository.findAll(
                specification,
                query.toPageable("id", Sort.Direction.ASC, Set.of("id", "productId", "currentStock")));
        Set<Integer> productIds = page.getContent().stream()
                .map(BranchInventoryModel::getProductId)
                .collect(Collectors.toSet());
        Map<Integer, ProductModel> productsById = loadProductsByIds(productIds);
        List<BranchInventoryItemResponse> rows = page.getContent().stream()
                .map(row -> toBranchResponse(row, branch, productsById.get(row.getProductId())))
                .toList();
        return new PageImpl<>(rows, page.getPageable(), page.getTotalElements());
    }

    @Override
    @Transactional
    public BranchInventoryItemResponse updateBranchReorderPoint(
            Long branchId,
            Integer productId,
            Integer reorderPoint
    ) {
        assertCanManageBranchReorderPoint(branchId);
        if (productId == null) {
            throw new BadRequestException("Product id is required.");
        }
        if (reorderPoint == null || reorderPoint < 0) {
            throw new BadRequestException("Reorder point must be zero or greater.");
        }

        BranchModel branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NotFoundException("Branch not found."));
        ProductModel product = productRepository.findByIdWithCategory(productId)
                .orElseThrow(() -> new NotFoundException("Product not found."));

        BranchInventoryModel row = branchInventoryRepository
                .findByBranchIdAndProductId(branchId, productId)
                .orElseGet(() -> {
                    BranchInventoryModel created = new BranchInventoryModel();
                    created.setBranchId(branchId);
                    created.setProductId(productId);
                    created.setCurrentStock(0);
                    return created;
                });
        row.setReorderPoint(reorderPoint);
        BranchInventoryModel saved = branchInventoryRepository.save(row);
        return toBranchResponse(saved, branch, product);
    }

    private BranchInventoryItemResponse toBranchResponse(
            BranchInventoryModel row,
            BranchModel branch,
            ProductModel product
    ) {
        int quantity = safeQty(row.getCurrentStock());
        int reorderPoint = safeQty(row.getReorderPoint());

        BranchInventoryItemResponse response = new BranchInventoryItemResponse();
        response.setInventoryId(row.getId());
        response.setBranchId(branch.getId());
        response.setBranchName(branch.getName());
        response.setProductId(row.getProductId());
        response.setProductCode(product == null ? null : product.getCode());
        response.setProductName(product == null ? null : product.getName());
        response.setCategoryName(product == null || product.getCategory() == null
                ? null
                : product.getCategory().getName());
        response.setUnit(product == null ? null : product.getUnit());
        response.setQuantity(quantity);
        response.setReorderPoint(reorderPoint);
        response.setLowStock(reorderPoint > 0 && quantity <= reorderPoint);
        if (product != null) {
            var topPackaging = productPackagingService.getTopPackaging(product);
            response.setTopPackagingLabel(topPackaging == null ? null : topPackaging.displayLabel());
            response.setTopPackagingConversionQty(productPackagingService.conversionQtyOf(topPackaging));
        }
        return response;
    }

    private Set<Integer> findMatchingProductIds(String search) {
        if (search == null) {
            return Set.of();
        }
        String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
        Specification<ProductModel> specification = (root, ignored, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern),
                cb.like(cb.lower(root.get("barcode")), pattern),
                cb.like(cb.lower(root.get("name")), pattern));
        return productRepository.findAll(specification).stream()
                .map(ProductModel::getId)
                .collect(Collectors.toSet());
    }

    private List<WarehouseInventoryItemResponse> mapWarehouseRows(List<WarehouseInventoryModel> inventoryRows) {
        Set<Integer> productIds = inventoryRows.stream()
                .map(WarehouseInventoryModel::getProductId)
                .collect(Collectors.toSet());
        Map<Integer, ProductModel> productsById = loadProductsByIds(productIds);

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

    private Map<Integer, ProductModel> loadProductsByIds(Set<Integer> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findByIdInWithCategory(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity(), (a, b) -> a, HashMap::new));
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

    private void assertCanManageBranchReorderPoint(Long branchId) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole role = currentUserProvider.getCurrentUserRole();
        if (role == UserRole.ADMIN || role == UserRole.DIRECTOR) {
            return;
        }
        if (role == UserRole.BRANCH_MANAGER && Objects.equals(currentUser.getBranchId(), branchId)) {
            return;
        }
        throw new ForbiddenException("Only the branch manager can update reorder points for this branch.");
    }

    private int safeQty(Integer value) {
        return value == null ? 0 : value;
    }
}
