package base.api.feature.product.service.impl;

import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.category.repository.ICategoryRepository;
import base.api.feature.product.dto.request.CreateProductRequest;
import base.api.feature.product.dto.request.UpdateProductRequest;
import base.api.feature.product.dto.response.ProductResponse;
import base.api.feature.product.mapper.ProductMapper;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.IProductService;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.shared.entity.BranchInventoryModel;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.CategoryModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.ProductPackagingModel;
import base.api.shared.entity.UserModel;
import base.api.shared.entity.WarehouseInventoryModel;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.enums.ProductScope;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ConflictException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import base.api.shared.util.Ean13BarcodeGenerator;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements IProductService {

    @Autowired
    private IProductRepository productRepository;

    @Autowired
    private ICategoryRepository categoryRepository;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private BranchInventoryRepository branchInventoryRepository;

    @Autowired
    private WarehouseInventoryRepository warehouseInventoryRepository;

    @Autowired
    private IBranchRepository branchRepository;

    @Autowired
    private ProductPackagingService productPackagingService;

    @Override
    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        assertCanManageProducts();
        UserModel actor = currentUserProvider.getCurrentUserOrThrow();
        UserRole role = currentUserProvider.getCurrentUserRole();

        String normalizedCode = normalizeRequiredText(request.getCode(), "Product code is required.");
        String normalizedName = normalizeRequiredText(request.getName(), "Product name is required.");
        String normalizedUnit = normalizeRequiredText(request.getUnit(), "Unit is required.");
        String normalizedBarcode = normalizeNullableText(request.getBarcode());

        validateDuplicateCode(normalizedCode);
        validateDuplicateBarcode(normalizedBarcode, null);
        validatePrices(request.getReferenceImportPrice(), request.getDefaultSalePrice());

        CategoryModel category = resolveCategory(request.getCategoryId());
        ProductScope scope = resolveCreateScope(role);
        Long branchId = resolveCreateBranchId(role, actor);

        ProductModel product = new ProductModel();
        product.setCode(normalizedCode);
        product.setBarcode(normalizedBarcode);
        product.setName(normalizedName);
        product.setCategory(category);
        product.setUnit(normalizedUnit);
        product.setImportUnit(normalizeNullableText(request.getImportUnit()));
        product.setUnitsPerImportUnit(request.getUnitsPerImportUnit());
        product.setReferenceImportPrice(request.getReferenceImportPrice());
        product.setDefaultSalePrice(request.getDefaultSalePrice());
        product.setDescription(normalizeNullableText(request.getDescription()));
        product.setImageUrl(normalizeNullableText(request.getImageUrl()));
        product.setStatus("active");
        product.setScope(scope.getValue());
        product.setBranchId(branchId);

        applyDefaultImportPackaging(product);
        ProductModel saved = productRepository.save(product);
        ensureInventoryRow(saved, scope, branchId);
        productPackagingService.ensureDefaultPackagings(saved);
        return productMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponse update(Integer id, UpdateProductRequest request) {
        assertCanManageProducts();
        ProductModel product = findProductOrThrow(id);
        assertCanMutateProduct(product);

        String normalizedName = normalizeRequiredText(request.getName(), "Product name is required.");
        String normalizedUnit = normalizeRequiredText(request.getUnit(), "Unit is required.");
        String normalizedBarcode = normalizeNullableText(request.getBarcode());
        String normalizedStatus = normalizeRequiredText(request.getStatus(), "Status is required.");

        validateDuplicateBarcode(normalizedBarcode, id);
        validatePrices(request.getReferenceImportPrice(), request.getDefaultSalePrice());

        product.setBarcode(normalizedBarcode);
        product.setName(normalizedName);
        product.setCategory(resolveCategory(request.getCategoryId()));
        product.setUnit(normalizedUnit);
        product.setImportUnit(normalizeNullableText(request.getImportUnit()));
        product.setUnitsPerImportUnit(request.getUnitsPerImportUnit());
        product.setReferenceImportPrice(request.getReferenceImportPrice());
        product.setDefaultSalePrice(request.getDefaultSalePrice());
        product.setDescription(normalizeNullableText(request.getDescription()));
        product.setImageUrl(normalizeNullableText(request.getImageUrl()));
        product.setStatus(normalizedStatus);
        applyDefaultImportPackaging(product);

        ProductModel saved = productRepository.save(product);
        productPackagingService.ensureDefaultPackagings(saved);
        return productMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        assertCanManageProducts();
        ProductModel product = findProductOrThrow(id);
        assertCanMutateProduct(product);
        productRepository.delete(product);
    }

    @Override
    public ProductResponse getById(Integer id) {
        ProductModel product = findProductOrThrow(id);
        assertCanViewProduct(product);
        ProductResponse response = enrichSingle(productMapper.toResponse(product));
        applyTopPackaging(response, productPackagingService.getTopPackaging(product));
        return response;
    }

    @Override
    public List<ProductResponse> getAll() {
        VisibilityContext visibility = resolveVisibility();
        List<ProductModel> products = productRepository.findVisibleProducts(
                visibility.supervisor(),
                visibility.branchId());

        Map<Integer, Integer> branchStock = loadBranchStockMap(visibility.branchId());
        Map<Integer, WarehouseInventoryModel> warehouseStock = loadWarehouseStockMap();
        Map<Integer, ProductPackagingModel> topPackagings = productPackagingService.getTopPackagingsByProductIds(
                products.stream().map(ProductModel::getId).toList());

        return products.stream()
                .map(product -> {
                    ProductResponse response = enrichList(
                            productMapper.toListResponse(product), branchStock, warehouseStock, visibility);
                    ProductPackagingModel top = productPackagingService.resolveTopPackaging(product, topPackagings);
                    applyTopPackaging(response, top);
                    return response;
                })
                .toList();
    }

    @Override
    public Page<ProductResponse> getPage(
            PageRequestDTO pageRequest,
            Integer categoryId,
            String status,
            String scope,
            boolean lowStockOnly
    ) {
        PageRequestDTO query = pageRequest == null ? new PageRequestDTO() : pageRequest;
        VisibilityContext visibility = resolveVisibility();
        Specification<ProductModel> specification = (root, ignored, cb) -> cb.conjunction();

        if (!visibility.supervisor()) {
            specification = specification.and((root, ignored, cb) -> cb.or(
                    cb.equal(cb.upper(root.get("scope")), ProductScope.GLOBAL.getValue()),
                    cb.equal(root.get("branchId"), visibility.branchId())
            ));
        }

        String search = query.normalizedSearch();
        if (search != null) {
            String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, ignored, cb) -> cb.or(
                    cb.like(cb.lower(root.get("code")), pattern),
                    cb.like(cb.lower(root.get("barcode")), pattern),
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            ));
        }
        if (categoryId != null) {
            specification = specification.and((root, ignored, cb) ->
                    cb.equal(root.get("category").get("id"), categoryId));
        }
        if (status != null && !status.isBlank()) {
            specification = specification.and((root, ignored, cb) ->
                    cb.equal(cb.lower(root.get("status")), status.trim().toLowerCase(Locale.ROOT)));
        }
        if (scope != null && !scope.isBlank()) {
            specification = specification.and((root, ignored, cb) ->
                    cb.equal(cb.upper(root.get("scope")), scope.trim().toUpperCase(Locale.ROOT)));
        }

        Map<Integer, WarehouseInventoryModel> warehouseStock = loadWarehouseStockMap();
        if (lowStockOnly) {
            Set<Integer> lowStockIds = warehouseStock.values().stream()
                    .filter(row -> row.getReorderPoint() != null && row.getReorderPoint() > 0)
                    .filter(row -> (row.getQuantity() == null ? 0 : row.getQuantity()) <= row.getReorderPoint())
                    .map(WarehouseInventoryModel::getProductId)
                    .collect(Collectors.toSet());
            if (lowStockIds.isEmpty()) {
                specification = specification.and((root, ignored, cb) -> cb.disjunction());
            } else {
                specification = specification.and((root, ignored, cb) -> root.get("id").in(lowStockIds));
            }
        }

        Page<ProductModel> products = productRepository.findAll(
                specification,
                query.toPageable(
                        "id",
                        Sort.Direction.ASC,
                        Set.of("id", "code", "name", "status", "createdAt", "updatedAt")));
        Map<Integer, Integer> branchStock = loadBranchStockMap(visibility.branchId());
        Map<Integer, ProductPackagingModel> topPackagings = productPackagingService.getTopPackagingsByProductIds(
                products.getContent().stream().map(ProductModel::getId).toList());

        return products.map(product -> {
            ProductResponse response = enrichList(
                    productMapper.toListResponse(product), branchStock, warehouseStock, visibility);
            ProductPackagingModel top = productPackagingService.resolveTopPackaging(product, topPackagings);
            applyTopPackaging(response, top);
            return response;
        });
    }

    @Override
    public ProductResponse scanByBarcode(String barcode) {
        String normalizedBarcode = normalizeRequiredText(barcode, "Barcode is required.");
        ProductModel product = productRepository.findByBarcode(normalizedBarcode)
                .orElseThrow(() -> new NotFoundException("Product not found for this barcode."));

        if (!"active".equalsIgnoreCase(product.getStatus())) {
            throw new BadRequestException("This product is inactive and cannot be added to the cart.");
        }

        assertCanViewProduct(product);
        ProductResponse response = enrichSingle(productMapper.toResponse(product));
        applyTopPackaging(response, productPackagingService.getTopPackaging(product));

        VisibilityContext visibility = resolveVisibility();
        if (visibility.branchId() != null && (response.getBranchStock() == null || response.getBranchStock() <= 0)) {
            throw new BadRequestException("This product is out of stock at your branch.");
        }

        return response;
    }

    private void applyTopPackaging(ProductResponse response, ProductPackagingModel topPackaging) {
        if (topPackaging == null) {
            return;
        }
        response.setTopPackagingLabel(topPackaging.displayLabel());
        response.setTopPackagingConversionQty(productPackagingService.conversionQtyOf(topPackaging));
    }

    @Override
    public String generateBarcode() {
        assertCanManageProducts();
        return Ean13BarcodeGenerator.nextBarcode(productRepository.findAllBarcodes());
    }

    private ProductResponse enrichSingle(ProductResponse response) {
        VisibilityContext visibility = resolveVisibility();
        Map<Integer, Integer> branchStock = loadBranchStockMap(visibility.branchId());
        Map<Integer, WarehouseInventoryModel> warehouseStock = loadWarehouseStockMap();
        return enrichList(response, branchStock, warehouseStock, visibility);
    }

    private ProductResponse enrichList(
            ProductResponse response,
            Map<Integer, Integer> branchStock,
            Map<Integer, WarehouseInventoryModel> warehouseStock,
            VisibilityContext visibility) {

        if (response.getBranchId() != null) {
            branchRepository.findById(response.getBranchId())
                    .map(BranchModel::getName)
                    .ifPresent(response::setBranchName);
        }

        if (visibility.branchId() != null) {
            response.setBranchStock(branchStock.getOrDefault(response.getId(), 0));
        }

        WarehouseInventoryModel warehouseRow = warehouseStock.get(response.getId());
        if (warehouseRow != null) {
            int quantity = warehouseRow.getQuantity() == null ? 0 : warehouseRow.getQuantity();
            int reorderPoint = warehouseRow.getReorderPoint() == null ? 0 : warehouseRow.getReorderPoint();
            response.setWarehouseStock(quantity);
            response.setLowStock(reorderPoint > 0 && quantity <= reorderPoint);
        } else if (visibility.supervisor()) {
            response.setWarehouseStock(0);
            response.setLowStock(false);
        }

        return response;
    }

    private void ensureInventoryRow(ProductModel product, ProductScope scope, Long branchId) {
        if (scope == ProductScope.BRANCH && branchId != null) {
            branchInventoryRepository.findByBranchIdAndProductId(branchId, product.getId())
                    .orElseGet(() -> {
                        BranchInventoryModel row = new BranchInventoryModel();
                        row.setBranchId(branchId);
                        row.setProductId(product.getId());
                        row.setCurrentStock(0);
                        return branchInventoryRepository.save(row);
                    });
        }

        warehouseInventoryRepository.findByProductId(product.getId())
                .orElseGet(() -> {
                    WarehouseInventoryModel row = new WarehouseInventoryModel();
                    row.setProductId(product.getId());
                    row.setQuantity(0);
                    row.setReorderPoint(50);
                    return warehouseInventoryRepository.save(row);
                });
    }

    private void applyDefaultImportPackaging(ProductModel product) {
        if (product.getImportUnit() != null && product.getUnitsPerImportUnit() != null) {
            return;
        }
        String unit = product.getUnit() == null ? "piece" : product.getUnit().toLowerCase();
        switch (unit) {
            case "can", "bottle" -> {
                product.setImportUnit("case");
                product.setUnitsPerImportUnit(24);
            }
            case "pack" -> {
                product.setImportUnit("carton");
                product.setUnitsPerImportUnit(30);
            }
            case "box" -> {
                product.setImportUnit("carton");
                product.setUnitsPerImportUnit(12);
            }
            case "piece" -> {
                product.setImportUnit("carton");
                product.setUnitsPerImportUnit(20);
            }
            default -> {
                product.setImportUnit("case");
                product.setUnitsPerImportUnit(24);
            }
        }
    }

    private ProductScope resolveCreateScope(UserRole role) {
        UserRole web = role == null ? null : role.toWebRole();
        if (web == UserRole.BRANCH_MANAGER || web == UserRole.INVENTORY_STAFF) {
            return ProductScope.BRANCH;
        }
        return ProductScope.GLOBAL;
    }

    private Long resolveCreateBranchId(UserRole role, UserModel actor) {
        ProductScope scope = resolveCreateScope(role);
        if (scope == ProductScope.GLOBAL) {
            return null;
        }
        if (actor.getBranchId() == null) {
            throw new BadRequestException("Branch is required for branch-local products.");
        }
        return actor.getBranchId();
    }

    private VisibilityContext resolveVisibility() {
        UserModel actor = currentUserProvider.getCurrentUserOrThrow();
        UserRole role = currentUserProvider.getCurrentUserRole();
        UserRole web = role == null ? null : role.toWebRole();
        boolean supervisor = web == UserRole.ADMIN
                || web == UserRole.DIRECTOR
                || web == UserRole.WAREHOUSE_MANAGER;
        Long branchId = null;
        if (web == UserRole.BRANCH_MANAGER || web == UserRole.INVENTORY_STAFF || web == UserRole.CASHIER) {
            branchId = actor.getBranchId();
        }
        return new VisibilityContext(supervisor, branchId);
    }

    private void assertCanManageProducts() {
        UserRole role = currentUserProvider.getCurrentUserRole();
        if (role == null) {
            throw new ForbiddenException("Access denied.");
        }
        UserRole web = role.toWebRole();
        if (web == UserRole.WAREHOUSE_MANAGER) {
            throw new ForbiddenException("Warehouse managers have read-only product access.");
        }
    }

    private void assertCanMutateProduct(ProductModel product) {
        UserModel actor = currentUserProvider.getCurrentUserOrThrow();
        UserRole web = currentUserProvider.getCurrentUserRole().toWebRole();

        if (web == UserRole.ADMIN || web == UserRole.DIRECTOR) {
            return;
        }

        if (web == UserRole.BRANCH_MANAGER || web == UserRole.INVENTORY_STAFF) {
            if (!ProductScope.BRANCH.getValue().equalsIgnoreCase(product.getScope())) {
                throw new ForbiddenException("You can only edit branch-local products created at your store.");
            }
            if (!Objects.equals(actor.getBranchId(), product.getBranchId())) {
                throw new ForbiddenException("You can only edit products for your branch.");
            }
            return;
        }

        throw new ForbiddenException("Access denied.");
    }

    private void assertCanViewProduct(ProductModel product) {
        VisibilityContext visibility = resolveVisibility();
        if (visibility.supervisor()) {
            return;
        }
        if (ProductScope.GLOBAL.getValue().equalsIgnoreCase(product.getScope())) {
            return;
        }
        if (visibility.branchId() != null && Objects.equals(visibility.branchId(), product.getBranchId())) {
            return;
        }
        throw new ForbiddenException("Access denied.");
    }

    private Map<Integer, Integer> loadBranchStockMap(Long branchId) {
        if (branchId == null) {
            return Map.of();
        }
        Map<Integer, Integer> map = new HashMap<>();
        for (BranchInventoryModel row : branchInventoryRepository.findByBranchId(branchId)) {
            map.put(row.getProductId(), row.getCurrentStock() == null ? 0 : row.getCurrentStock());
        }
        return map;
    }

    private Map<Integer, WarehouseInventoryModel> loadWarehouseStockMap() {
        UserRole web = currentUserProvider.getCurrentUserRole().toWebRole();
        if (web != UserRole.ADMIN && web != UserRole.DIRECTOR && web != UserRole.WAREHOUSE_MANAGER) {
            return Map.of();
        }
        Map<Integer, WarehouseInventoryModel> map = new HashMap<>();
        for (WarehouseInventoryModel row : warehouseInventoryRepository.findAll()) {
            map.put(row.getProductId(), row);
        }
        return map;
    }

    private ProductModel findProductOrThrow(Integer id) {
        return productRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new NotFoundException("Product not found."));
    }

    private void validateDuplicateCode(String code) {
        if (productRepository.existsByCode(code)) {
            throw new ConflictException("Product code already exists.");
        }
    }

    private void validateDuplicateBarcode(String barcode, Integer currentId) {
        if (barcode == null) {
            return;
        }

        boolean exists = currentId == null
                ? productRepository.existsByBarcode(barcode)
                : productRepository.existsByBarcodeAndIdNot(barcode, currentId);

        if (exists) {
            throw new ConflictException("Barcode already exists.");
        }
    }

    private void validatePrices(BigDecimal referenceImportPrice, BigDecimal defaultSalePrice) {
        if (referenceImportPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Reference import price must be greater than or equal to 0.");
        }

        if (defaultSalePrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Default sale price must be greater than or equal to 0.");
        }

        if (defaultSalePrice.compareTo(referenceImportPrice) < 0) {
            throw new BadRequestException("Default sale price must not be smaller than reference import price.");
        }
    }

    private CategoryModel resolveCategory(Integer categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BadRequestException("Category not found."));
    }

    private String normalizeRequiredText(String value, String blankMessage) {
        String normalized = normalizeWhitespace(value);
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException(blankMessage);
        }
        return normalized;
    }

    private String normalizeNullableText(String value) {
        String normalized = normalizeWhitespace(value);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        return normalized;
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private record VisibilityContext(boolean supervisor, Long branchId) {
    }
}
