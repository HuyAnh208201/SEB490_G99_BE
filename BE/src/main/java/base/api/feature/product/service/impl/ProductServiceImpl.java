package base.api.feature.product.service.impl;

import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.category.repository.ICategoryRepository;
import base.api.feature.product.dto.request.CreateProductRequest;
import base.api.feature.product.dto.request.UpdateProductRequest;
import base.api.feature.product.dto.response.PosCatalogItemResponse;
import base.api.feature.product.dto.response.ProductResponse;
import base.api.feature.product.mapper.ProductMapper;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.IProductService;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.product.service.ProductSalePriceService;
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
import base.api.shared.util.CategoryReorderPoints;
import base.api.shared.util.Ean13BarcodeGenerator;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
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

    @Autowired
    private ProductSalePriceService productSalePriceService;

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
        validateDuplicateName(normalizedName, null);
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
        product.setSupplierId(request.getSupplierId());
        product.setReferenceImportPrice(request.getReferenceImportPrice());
        product.setDefaultSalePrice(request.getDefaultSalePrice());
        product.setRefundable(!Boolean.FALSE.equals(request.getRefundable()));
        product.setDescription(normalizeNullableText(request.getDescription()));
        product.setImageUrl(normalizeNullableText(request.getImageUrl()));
        product.setStatus("active");
        product.setScope(scope.getValue());
        product.setBranchId(branchId);

        applyDefaultImportPackaging(product);
        ProductModel saved = productRepository.save(product);
        ensureInventoryRow(saved, scope, branchId);
        productPackagingService.ensureDefaultPackagings(saved);
        return applySalePrice(productMapper.toResponse(saved), saved);
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
        String existingName = product.getName() == null ? "" : product.getName().trim();
        if (!existingName.equalsIgnoreCase(normalizedName)) {
            validateDuplicateName(normalizedName, id);
        }
        BigDecimal effectiveExistingPrice = productSalePriceService == null
                ? product.getDefaultSalePrice() : productSalePriceService.effectivePrice(product);
        BigDecimal currentSalePrice = effectiveExistingPrice == null
                ? request.getDefaultSalePrice() : effectiveExistingPrice;
        validatePrices(request.getReferenceImportPrice(), currentSalePrice);

        product.setBarcode(normalizedBarcode);
        product.setName(normalizedName);
        product.setCategory(resolveCategory(request.getCategoryId()));
        product.setUnit(normalizedUnit);
        product.setImportUnit(normalizeNullableText(request.getImportUnit()));
        product.setUnitsPerImportUnit(request.getUnitsPerImportUnit());
        product.setSupplierId(request.getSupplierId());
        product.setReferenceImportPrice(request.getReferenceImportPrice());
        // Existing retail prices are immutable during the day. Warehouse managers
        // schedule future prices through ProductSalePriceService instead.
        if (product.getDefaultSalePrice() == null) {
            product.setDefaultSalePrice(currentSalePrice);
        }
        if (request.getRefundable() != null) {
            product.setRefundable(request.getRefundable());
        }
        product.setDescription(normalizeNullableText(request.getDescription()));
        product.setImageUrl(normalizeNullableText(request.getImageUrl()));
        product.setStatus(normalizedStatus);
        applyDefaultImportPackaging(product);

        ProductModel saved = productRepository.save(product);
        productPackagingService.ensureDefaultPackagings(saved);
        if (isShortDateCategory(saved.getCategory())) {
            warehouseInventoryRepository.findByProductId(saved.getId())
                    .ifPresent(warehouseInventoryRepository::delete);
        }
        return applySalePrice(productMapper.toResponse(saved), saved);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        assertCanManageProducts();
        ProductModel product = findProductOrThrow(id);
        assertCanMutateProduct(product);
        if (branchInventoryRepository.existsByProductId(id)) {
            throw new ConflictException(
                    "Cannot delete this product because it still has branch inventory records. Remove stock references first.");
        }
        if (warehouseInventoryRepository.existsByProductId(id)) {
            throw new ConflictException(
                    "Cannot delete this product because it still has warehouse inventory records. Remove stock references first.");
        }
        productRepository.delete(product);
    }

    @Override
    public ProductResponse getById(Integer id) {
        ProductModel product = findProductOrThrow(id);
        assertCanViewProduct(product);
        ProductResponse response = enrichSingle(productMapper.toResponse(product));
        applyTopPackaging(response, productPackagingService.getTopPackaging(product));
        return applySalePrice(response, product);
    }

    @Override
    public List<ProductResponse> getAll() {
        // Soft cap: legacy callers must not pull the entire catalog over the shared DB.
        PageRequestDTO capped = new PageRequestDTO();
        capped.setPage(1);
        capped.setSize(PageRequestDTO.MAX_PAGE_SIZE);
        return getPage(capped, null, null, null, false, null).getContent();
    }

    @Override
    public long countVisible() {
        VisibilityContext visibility = resolveVisibility();
        return productRepository.countVisibleProducts(visibility.supervisor(), visibility.branchId());
    }

    @Override
    public List<PosCatalogItemResponse> getPosCatalog() {
        PageRequestDTO capped = new PageRequestDTO();
        capped.setPage(1);
        capped.setSize(PageRequestDTO.MAX_PAGE_SIZE);
        return getPosCatalogPage(capped, null).getContent();
    }

    @Override
    public Page<PosCatalogItemResponse> getPosCatalogPage(PageRequestDTO pageRequest, Integer categoryId) {
        PageRequestDTO query = pageRequest == null ? new PageRequestDTO() : pageRequest;
        VisibilityContext visibility = resolveVisibility();
        Pageable pageable = query.toPageable(
                "code",
                Sort.Direction.ASC,
                Set.of("id", "code", "name"));
        String looseKeyword = base.api.shared.util.ProductSearchNormalizer.toLooseLikePattern(
                query.normalizedSearch());
        Page<ProductModel> products = productRepository.findVisibleActiveProducts(
                visibility.supervisor(),
                visibility.branchId(),
                categoryId,
                looseKeyword,
                pageable);
        Map<Integer, Integer> branchStock = loadBranchStockMap(visibility.branchId());

        return products.map(product -> {
            PosCatalogItemResponse item = new PosCatalogItemResponse();
            item.setId(product.getId());
            item.setCode(product.getCode());
            item.setBarcode(product.getBarcode());
            item.setName(product.getName());
            item.setUnit(product.getUnit());
            item.setDefaultSalePrice(productSalePriceService == null
                    ? product.getDefaultSalePrice() : productSalePriceService.effectivePrice(product));
            item.setRefundable(!Boolean.FALSE.equals(product.getRefundable()));
            item.setImageUrl(product.getImageUrl());
            item.setBranchStock(branchStock.getOrDefault(product.getId(), 0));
            if (product.getCategory() != null) {
                item.setCategoryId(product.getCategory().getId());
                item.setCategoryName(product.getCategory().getName());
            }
            return item;
        });
    }

    @Override
    public Page<ProductResponse> getPage(
            PageRequestDTO pageRequest,
            Integer categoryId,
            String status,
            String scope,
            boolean lowStockOnly,
            String stockSort
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
            String pattern = base.api.shared.util.ProductSearchNormalizer.toLooseLikePattern(search);
            if (pattern == null) {
                pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
            }
            final String likePattern = pattern;
            specification = specification.and((root, ignored, cb) -> cb.or(
                    cb.like(cb.lower(root.get("code")), likePattern),
                    cb.like(cb.lower(root.get("barcode")), likePattern),
                    cb.like(cb.lower(root.get("name")), likePattern),
                    cb.like(cb.lower(root.get("description")), likePattern)
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

        Map<Integer, Integer> branchStock = loadBranchStockMap(visibility.branchId());
        Map<Integer, Integer> branchReorder = loadBranchReorderMap(visibility.branchId());

        String normalizedStockSort = stockSort == null ? "" : stockSort.trim().toLowerCase(Locale.ROOT);
        boolean sortByStock = "asc".equals(normalizedStockSort) || "desc".equals(normalizedStockSort);

        Page<ProductModel> products;
        if (sortByStock) {
            List<ProductModel> allMatching = productRepository.findAll(specification, Sort.by(Sort.Direction.ASC, "id"));
            boolean ascending = "asc".equals(normalizedStockSort);
            // Branch roles sort by branch stock; WM/supervisor by warehouse stock.
            Comparator<ProductModel> byStock = Comparator.comparingInt(product ->
                    stockQtyForSort(product.getId(), visibility, warehouseStock, branchStock));
            if (!ascending) {
                byStock = byStock.reversed();
            }
            byStock = byStock.thenComparing(ProductModel::getId, Comparator.nullsLast(Integer::compareTo));
            allMatching.sort(byStock);

            int size = Math.min(PageRequestDTO.MAX_PAGE_SIZE, Math.max(1, query.getSize()));
            int zeroBasedPage = Math.max(0, query.getPage() - 1);
            int from = Math.min(zeroBasedPage * size, allMatching.size());
            int to = Math.min(from + size, allMatching.size());
            List<ProductModel> pageContent = allMatching.subList(from, to);
            Pageable pageable = PageRequest.of(zeroBasedPage, size);
            products = new PageImpl<>(pageContent, pageable, allMatching.size());
        } else {
            products = productRepository.findAll(
                    specification,
                    query.toPageable(
                            "id",
                            Sort.Direction.ASC,
                            Set.of("id", "code", "name", "status", "createdAt", "updatedAt")));
        }

        Map<Integer, ProductPackagingModel> topPackagings = productPackagingService.getTopPackagingsByProductIds(
                products.getContent().stream().map(ProductModel::getId).toList());
        Map<Long, String> branchNames = loadBranchNameMap(
                products.getContent().stream().map(ProductModel::getBranchId).toList());

        return products.map(product -> {
            ProductResponse response = enrichList(
                    productMapper.toListResponse(product),
                    branchStock,
                    branchReorder,
                    warehouseStock,
                    visibility,
                    branchNames);
            ProductPackagingModel top = productPackagingService.resolveTopPackaging(product, topPackagings);
            applyTopPackaging(response, top);
            return applySalePrice(response, product);
        });
    }

    private ProductResponse applySalePrice(ProductResponse response, ProductModel product) {
        if (productSalePriceService == null || product == null) return response;
        response.setDefaultSalePrice(productSalePriceService.effectivePrice(product));
        base.api.shared.entity.ProductSalePriceModel next = productSalePriceService.nextPrice(product.getId());
        if (next != null) {
            response.setScheduledSalePrice(next.getPrice());
            response.setScheduledSalePriceEffectiveDate(next.getEffectiveDate());
        }
        return response;
    }

    private int stockQtyForSort(
            Integer productId,
            VisibilityContext visibility,
            Map<Integer, WarehouseInventoryModel> warehouseStock,
            Map<Integer, Integer> branchStock
    ) {
        if (visibility.branchId() != null) {
            return branchStock.getOrDefault(productId, 0);
        }
        WarehouseInventoryModel row = warehouseStock.get(productId);
        if (row == null || row.getQuantity() == null) {
            return 0;
        }
        return row.getQuantity();
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
        Map<Integer, Integer> branchReorder = loadBranchReorderMap(visibility.branchId());
        Map<Integer, WarehouseInventoryModel> warehouseStock = loadWarehouseStockMap();
        Map<Long, String> branchNames = loadBranchNameMap(
                response.getBranchId() == null ? List.of() : List.of(response.getBranchId()));
        return enrichList(response, branchStock, branchReorder, warehouseStock, visibility, branchNames);
    }

    private Map<Long, String> loadBranchNameMap(List<Long> branchIds) {
        if (branchIds == null || branchIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = branchIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (BranchModel branch : branchRepository.findAllById(distinct)) {
            names.put(branch.getId(), branch.getName());
        }
        return names;
    }

    private ProductResponse enrichList(
            ProductResponse response,
            Map<Integer, Integer> branchStock,
            Map<Integer, Integer> branchReorder,
            Map<Integer, WarehouseInventoryModel> warehouseStock,
            VisibilityContext visibility,
            Map<Long, String> branchNames) {

        if (response.getBranchId() != null) {
            String name = branchNames != null ? branchNames.get(response.getBranchId()) : null;
            if (name != null) {
                response.setBranchName(name);
            }
        }

        if (visibility.branchId() != null) {
            response.setBranchStock(branchStock.getOrDefault(response.getId(), 0));
            int resolvedReorder = CategoryReorderPoints.forBranch(
                    response.getCategoryName(), response.getUnitsPerImportUnit());
            response.setBranchReorderPoint(resolvedReorder);
            if (resolvedReorder > 0) {
                int stock = branchStock.getOrDefault(response.getId(), 0);
                response.setLowStock(stock <= resolvedReorder);
            }
        }

        WarehouseInventoryModel warehouseRow = warehouseStock.get(response.getId());
        if (warehouseRow != null) {
            int quantity = warehouseRow.getQuantity() == null ? 0 : warehouseRow.getQuantity();
            int reorderPoint = warehouseRow.getReorderPoint() == null ? 0 : warehouseRow.getReorderPoint();
            response.setWarehouseStock(quantity);
            response.setWarehouseReorderPoint(reorderPoint);
            // Warehouse low-stock takes precedence for WM/supervisor views
            if (visibility.supervisor() || visibility.branchId() == null) {
                response.setLowStock(reorderPoint > 0 && quantity <= reorderPoint);
            } else if (response.getLowStock() == null) {
                response.setLowStock(reorderPoint > 0 && quantity <= reorderPoint);
            }
        } else if (visibility.supervisor()) {
            response.setWarehouseStock(0);
            response.setWarehouseReorderPoint(null);
            if (response.getLowStock() == null) {
                response.setLowStock(false);
            }
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
                        String categoryName = product.getCategory() == null ? null : product.getCategory().getName();
                        row.setReorderPoint(CategoryReorderPoints.forBranch(
                                categoryName, product.getUnitsPerImportUnit()));
                        return branchInventoryRepository.save(row);
                    });
        }

        warehouseInventoryRepository.findByProductId(product.getId())
                .ifPresentOrElse(
                        existing -> {
                            if (isShortDateCategory(product.getCategory())) {
                                warehouseInventoryRepository.delete(existing);
                            }
                        },
                        () -> {
                            if (isShortDateCategory(product.getCategory())) {
                                return;
                            }
                            WarehouseInventoryModel row = new WarehouseInventoryModel();
                            row.setProductId(product.getId());
                            row.setQuantity(0);
                            String categoryName = product.getCategory() == null ? null : product.getCategory().getName();
                            row.setReorderPoint(CategoryReorderPoints.forWarehouse(
                                    categoryName, product.getUnitsPerImportUnit()));
                            warehouseInventoryRepository.save(row);
                        });
    }

    private boolean isShortDateCategory(CategoryModel category) {
        return category != null && Boolean.TRUE.equals(category.getShortDate());
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
        if (web == UserRole.BRANCH_MANAGER || web == UserRole.INVENTORY_STAFF) {
            throw new ForbiddenException("Branch store roles have view-only product access.");
        }
    }

    private void assertCanMutateProduct(ProductModel product) {
        UserRole web = currentUserProvider.getCurrentUserRole().toWebRole();

        if (web == UserRole.ADMIN || web == UserRole.DIRECTOR) {
            return;
        }

        if (web == UserRole.BRANCH_MANAGER || web == UserRole.INVENTORY_STAFF) {
            throw new ForbiddenException("Branch store roles have view-only product access.");
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

    private Map<Integer, Integer> loadBranchReorderMap(Long branchId) {
        if (branchId == null) {
            return Map.of();
        }
        Map<Integer, Integer> map = new HashMap<>();
        for (BranchInventoryModel row : branchInventoryRepository.findByBranchId(branchId)) {
            map.put(row.getProductId(), row.getReorderPoint() == null ? 0 : row.getReorderPoint());
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

    private void validateDuplicateName(String name, Integer currentId) {
        boolean exists = currentId == null
                ? productRepository.existsByNameIgnoreCase(name)
                : productRepository.existsByNameIgnoreCaseAndIdNot(name, currentId);
        if (exists) {
            throw new ConflictException("A product with this name already exists.");
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
        CategoryModel category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BadRequestException("Category not found."));
        if (Boolean.FALSE.equals(category.getActive())) {
            throw new BadRequestException("Cannot assign an inactive category.");
        }
        return category;
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
