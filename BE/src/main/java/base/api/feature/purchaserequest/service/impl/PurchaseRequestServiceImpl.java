package base.api.feature.purchaserequest.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.posorder.repository.OrderItemRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.dispatch.service.WarehouseStockAllocationHelper;
import base.api.feature.purchaserequest.dto.request.ApprovePurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.request.CreatePurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.request.PurchaseRequestItemRequest;
import base.api.feature.purchaserequest.dto.request.ReceiveGoodsRequest;
import base.api.feature.purchaserequest.dto.request.RejectPurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.request.SaveDraftRequest;
import base.api.feature.purchaserequest.dto.request.SubmitPurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.response.ConsolidatedBranchResponse;
import base.api.feature.purchaserequest.dto.response.ProductSearchResponse;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestBranchResponse;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestResponse;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestSummaryResponse;
import base.api.feature.purchaserequest.dto.response.RecommendedProductResponse;
import base.api.feature.purchaserequest.mapper.PurchaseRequestMapper;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.purchaserequest.repository.GoodsReceiptItemRepository;
import base.api.feature.purchaserequest.repository.GoodsReceiptRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestDetailRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.feature.purchaserequest.service.IPurchaseRequestService;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.entity.BranchInventoryModel;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.GoodsReceiptItemModel;
import base.api.shared.entity.GoodsReceiptModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.ProductPackagingModel;
import base.api.shared.entity.PurchaseRequestDetailModel;
import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.entity.UserModel;
import base.api.shared.entity.WarehouseInventoryModel;
import base.api.shared.enums.PurchaseRequestStatus;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import base.api.shared.util.CategoryReorderPoints;
import base.api.shared.util.ProductSearchNormalizer;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PurchaseRequestServiceImpl implements IPurchaseRequestService {

    private static final int MAX_RECOMMENDED_PRODUCTS = 100;

    private static final Set<PurchaseRequestStatus> WAREHOUSE_VISIBLE_STATUSES = EnumSet.of(
            PurchaseRequestStatus.PENDING,
            PurchaseRequestStatus.APPROVED,
            PurchaseRequestStatus.AWAITING_STOCK,
            PurchaseRequestStatus.RECEIVED
    );

    /** Incoming Requests screen: pending review, short-stock, and ready-to-ship. */
    static final Set<PurchaseRequestStatus> WAREHOUSE_INCOMING_STATUSES = EnumSet.of(
            PurchaseRequestStatus.PENDING,
            PurchaseRequestStatus.AWAITING_STOCK,
            PurchaseRequestStatus.APPROVED
    );

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private PurchaseRequestDetailRepository detailRepository;

    @Autowired
    private BranchInventoryRepository branchInventoryRepository;

    @Autowired
    private WarehouseInventoryRepository warehouseInventoryRepository;

    @Autowired
    private WarehouseStockAllocationHelper warehouseStockAllocationHelper;

    @Autowired
    private GoodsReceiptRepository goodsReceiptRepository;

    @Autowired
    private GoodsReceiptItemRepository goodsReceiptItemRepository;

    @Autowired
    private IProductRepository productRepository;

    @Autowired
    private ProductPackagingService productPackagingService;

    @Autowired
    private IBranchRepository branchRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PurchaseRequestMapper purchaseRequestMapper;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Value("${purchase-request.default-reorder-point:10}")
    private Integer defaultReorderPoint;

    @Override
    @Transactional
    public PurchaseRequestResponse createDraft(CreatePurchaseRequestRequest request) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        Long branchId = resolveBranchManagerBranchId(currentUser);

        PurchaseRequestModel purchaseRequest = new PurchaseRequestModel();
        purchaseRequest.setBranchId(branchId);
        purchaseRequest.setReason(normalizeNullableText(request.getNotes()));
        purchaseRequest.setStatus(PurchaseRequestStatus.DRAFT);
        purchaseRequest.setCreatedBy(currentUser.getId());
        purchaseRequest.setDesiredReceiveDate(request.getDesiredReceiveDate());

        PurchaseRequestModel savedRequest = purchaseRequestRepository.save(purchaseRequest);
        replaceDetails(savedRequest.getId(), buildRequestedQuantities(request.getItems(), request.getAddAllRecommended(), branchId));
        return buildResponse(savedRequest);
    }

    @Override
    @Transactional
    public PurchaseRequestResponse saveDraft(Long id, SaveDraftRequest request) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        PurchaseRequestModel purchaseRequest = findRequestOrThrow(id);
        assertCanViewRequest(purchaseRequest, currentUser);
        assertDraftEditable(purchaseRequest);

        purchaseRequest.setReason(normalizeNullableText(request.getNotes()));
        purchaseRequest.setDesiredReceiveDate(request.getDesiredReceiveDate());
        PurchaseRequestModel savedRequest = purchaseRequestRepository.save(purchaseRequest);
        replaceDetails(savedRequest.getId(), buildRequestedQuantities(request.getItems(), request.getAddAllRecommended(), savedRequest.getBranchId()));
        return buildResponse(savedRequest);
    }

    @Override
    @Transactional
    public PurchaseRequestResponse submitRequest(Long id, SubmitPurchaseRequestRequest request) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        PurchaseRequestModel purchaseRequest = findRequestOrThrow(id);
        assertCanViewRequest(purchaseRequest, currentUser);
        assertDraftEditable(purchaseRequest);

        List<PurchaseRequestDetailModel> details = detailRepository.findByPurchaseRequestIdOrderByIdAsc(id);
        if (details.isEmpty()) {
            throw new BadRequestException("Cannot submit empty request.");
        }
        if (details.stream().anyMatch(detail -> detail.getRequestedQty() == null || detail.getRequestedQty() <= 0)) {
            throw new BadRequestException("Quantity must be greater than zero.");
        }
        if (purchaseRequest.getDesiredReceiveDate() == null) {
            throw new BadRequestException("Desired receive date is required before submitting.");
        }

        purchaseRequest.setStatus(PurchaseRequestStatus.PENDING);
        if (purchaseRequest.getSubmittedAt() == null) {
            purchaseRequest.setSubmittedAt(LocalDateTime.now());
        }
        return buildResponse(purchaseRequestRepository.save(purchaseRequest));
    }

    @Override
    @Transactional
    public PurchaseRequestResponse cancelRequest(Long id) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        PurchaseRequestModel purchaseRequest = findRequestOrThrow(id);
        assertCanViewRequest(purchaseRequest, currentUser);

        if (purchaseRequest.getStatus() == null || !purchaseRequest.getStatus().isCancellable()) {
            throw new BadRequestException("Only draft requests can be cancelled.");
        }

        purchaseRequest.setStatus(PurchaseRequestStatus.CANCELLED);
        return buildResponse(purchaseRequestRepository.save(purchaseRequest));
    }

    @Override
    public PurchaseRequestResponse getRequest(Long id) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        PurchaseRequestModel purchaseRequest = findRequestOrThrow(id);
        assertCanViewRequest(purchaseRequest, currentUser);
        return buildResponse(purchaseRequest);
    }

    @Override
    public Page<PurchaseRequestSummaryResponse> getRequestHistory(PageRequestDTO pageRequest) {
        return getRequestHistory(pageRequest, null, null);
    }

    @Override
    public Page<PurchaseRequestSummaryResponse> getRequestHistory(
            PageRequestDTO pageRequest,
            PurchaseRequestStatus status,
            Long requestedBranchId
    ) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole rawRole = currentUserProvider.getCurrentUserRole();
        UserRole role = rawRole == null ? null : rawRole.toWebRole();
        Pageable pageable = newestFirst(pageRequest);
        Specification<PurchaseRequestModel> specification = (root, ignored, cb) -> cb.conjunction();
        if (role == UserRole.ADMIN || role == UserRole.DIRECTOR) {
            if (requestedBranchId != null) {
                specification = specification.and((root, ignored, cb) ->
                        cb.equal(root.get("branchId"), requestedBranchId));
            }
        } else if (role == UserRole.WAREHOUSE_MANAGER) {
            specification = specification.and((root, ignored, cb) ->
                    root.get("status").in(WAREHOUSE_VISIBLE_STATUSES));
            if (requestedBranchId != null) {
                specification = specification.and((root, ignored, cb) ->
                        cb.equal(root.get("branchId"), requestedBranchId));
            }
        } else if (role == UserRole.BRANCH_MANAGER) {
            Long branchId = resolveBranchManagerBranchId(currentUser);
            specification = specification.and((root, ignored, cb) -> cb.equal(root.get("branchId"), branchId));
        } else if (role == UserRole.INVENTORY_STAFF) {
            Long branchId = resolveBranchStaffBranchId(currentUser);
            specification = specification.and((root, ignored, cb) -> cb.equal(root.get("branchId"), branchId));
        } else {
            throw new ForbiddenException("Access denied.");
        }

        if (status != null) {
            specification = specification.and((root, ignored, cb) -> cb.equal(root.get("status"), status));
        } else if (role == UserRole.BRANCH_MANAGER) {
            specification = specification.and((root, ignored, cb) ->
                    cb.equal(root.get("status"), PurchaseRequestStatus.DRAFT));
        } else if (role == UserRole.WAREHOUSE_MANAGER) {
            specification = specification.and((root, ignored, cb) ->
                    root.get("status").in(WAREHOUSE_INCOMING_STATUSES));
        } else if (role == UserRole.INVENTORY_STAFF) {
            specification = specification.and((root, ignored, cb) ->
                    cb.notEqual(root.get("status"), PurchaseRequestStatus.DRAFT));
        }
        String search = pageRequest == null ? null : pageRequest.normalizedSearch();
        if (search != null) {
            String pattern = "%" + search.toLowerCase(java.util.Locale.ROOT) + "%";
            Long searchedId = extractNumericId(search);
            specification = specification.and((root, criteriaQuery, cb) -> {
                var branchSubquery = criteriaQuery.subquery(Long.class);
                var branch = branchSubquery.from(BranchModel.class);
                branchSubquery.select(branch.get("id"))
                        .where(cb.or(
                                cb.like(cb.lower(branch.get("name")), pattern),
                                cb.like(cb.lower(branch.get("address")), pattern)));

                var creatorSubquery = criteriaQuery.subquery(Long.class);
                var creator = creatorSubquery.from(UserModel.class);
                creatorSubquery.select(creator.get("id"))
                        .where(cb.like(cb.lower(creator.get("fullName")), pattern));

                var textMatch = cb.or(
                        cb.like(cb.lower(root.get("reason")), pattern),
                        root.get("branchId").in(branchSubquery),
                        root.get("createdBy").in(creatorSubquery));
                return searchedId == null
                        ? textMatch
                        : cb.or(textMatch, cb.equal(root.get("id"), searchedId));
            });
        }

        Page<PurchaseRequestModel> requests = purchaseRequestRepository.findAll(specification, pageable);

        List<PurchaseRequestModel> content = requests.getContent();
        Set<Long> branchIds = content.stream().map(PurchaseRequestModel::getBranchId).collect(Collectors.toSet());
        Set<Long> userIds = content.stream().map(PurchaseRequestModel::getCreatedBy).collect(Collectors.toSet());
        Map<Long, BranchModel> branchesById = branchRepository.findAllById(branchIds).stream()
                .collect(Collectors.toMap(BranchModel::getId, Function.identity(), (a, b) -> a));
        Map<Long, UserModel> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserModel::getId, Function.identity(), (a, b) -> a));
        List<Long> requestIds = content.stream().map(PurchaseRequestModel::getId).toList();
        Map<Long, Integer> itemCountsByRequest = requestIds.isEmpty()
                ? Map.of()
                : detailRepository.findByPurchaseRequestIdIn(requestIds).stream()
                        .collect(Collectors.groupingBy(
                                PurchaseRequestDetailModel::getPurchaseRequestId,
                                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        return requests.map(request -> buildSummaryResponse(
                request,
                branchesById.get(request.getBranchId()),
                usersById.get(request.getCreatedBy()),
                itemCountsByRequest.getOrDefault(request.getId(), 0)));
    }

    @Override
    public List<PurchaseRequestBranchResponse> getWarehouseFilterBranches() {
        List<Long> branchIds = purchaseRequestRepository.findDistinctBranchIdsByStatusIn(WAREHOUSE_VISIBLE_STATUSES);
        if (branchIds.isEmpty()) {
            return List.of();
        }
        return branchRepository.findAllById(branchIds).stream()
                .sorted(Comparator.comparing(BranchModel::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(branch -> {
                    PurchaseRequestBranchResponse row = new PurchaseRequestBranchResponse();
                    row.setId(branch.getId());
                    row.setName(branch.getName());
                    row.setAddress(branch.getAddress());
                    return row;
                })
                .toList();
    }

    @Override
    public List<RecommendedProductResponse> getRecommendedProducts() {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        Long branchId = resolveBranchManagerBranchId(currentUser);
        return getRecommendedProductsForBranch(branchId);
    }

    @Override
    public Page<ProductSearchResponse> searchProducts(
            String keyword,
            PageRequestDTO pageRequest,
            Integer categoryId,
            Boolean lowStockOnly,
            String stockSort
    ) {
        Pageable pageable = productSearchPage(pageRequest);
        // When sorting/filtering by stock we still need the page of products from DB first
        // (not the entire catalog). lowStockOnly then filters within that page — just enough
        // to avoid 500s under shared-DB load.
        String looseKeyword = ProductSearchNormalizer.toLooseLikePattern(normalizeNullableText(keyword));
        Page<ProductModel> productPage = productRepository.searchActiveProductsFiltered(
                looseKeyword, categoryId, pageable);
        if (productPage.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<ProductModel> products = productPage.getContent();
        Set<Integer> productIds = products.stream()
                .map(ProductModel::getId)
                .collect(Collectors.toSet());
        Map<Integer, ProductPackagingModel> topPackagings =
                productPackagingService.getTopPackagingsByProductIds(productIds);

        Long branchId = currentUserProvider.getCurrentUserOrThrow().getBranchId();
        Map<Integer, BranchInventoryModel> inventoryByProductId = new HashMap<>();
        if (branchId != null) {
            for (BranchInventoryModel row : branchInventoryRepository.findByBranchIdAndProductIdIn(
                    branchId, productIds)) {
                inventoryByProductId.put(row.getProductId(), row);
            }
        }

        List<ProductSearchResponse> enriched = new ArrayList<>(products.size());
        for (ProductModel product : products) {
            ProductSearchResponse response = purchaseRequestMapper.toProductSearchResponse(
                    product, topPackagings.get(product.getId()));
            BranchInventoryModel inventory = inventoryByProductId.get(product.getId());
            int currentStock = inventory == null ? 0 : safeStock(inventory.getCurrentStock());
            int reorderPoint = resolveBranchReorderPoint(product, inventory);
            response.setCurrentStock(currentStock);
            response.setReorderPoint(reorderPoint);
            response.setLowStock(reorderPoint > 0 && currentStock <= reorderPoint);
            enriched.add(response);
        }

        if (Boolean.TRUE.equals(lowStockOnly)) {
            enriched = enriched.stream()
                    .filter(row -> Boolean.TRUE.equals(row.getLowStock()))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        String sort = stockSort == null || stockSort.isBlank()
                ? "asc"
                : stockSort.trim().toLowerCase(Locale.ROOT);
        if ("asc".equals(sort) || "desc".equals(sort)) {
            Comparator<ProductSearchResponse> byStock = Comparator.comparingInt(
                    row -> row.getCurrentStock() == null ? 0 : row.getCurrentStock());
            if ("desc".equals(sort)) {
                byStock = byStock.reversed();
            }
            enriched.sort(byStock.thenComparing(
                    ProductSearchResponse::getProductName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        }

        return new PageImpl<>(enriched, pageable, productPage.getTotalElements());
    }

    @Override
    public List<ConsolidatedBranchResponse> getConsolidatedRequests() {
        List<PurchaseRequestModel> approvedRequests = purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED);
        if (approvedRequests.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> branchIdByRequestId = new HashMap<>();
        for (PurchaseRequestModel request : approvedRequests) {
            branchIdByRequestId.put(request.getId(), request.getBranchId());
        }

        List<PurchaseRequestDetailModel> details =
                detailRepository.findByPurchaseRequestIdIn(branchIdByRequestId.keySet());

        // branchId -> productId -> tổng approved_quantity
        Map<Long, Map<Integer, Integer>> quantityByBranchProduct = new LinkedHashMap<>();
        for (PurchaseRequestDetailModel detail : details) {
            Integer approvedQty = detail.getApprovedQuantity();
            if (approvedQty == null || approvedQty <= 0 || detail.getProductId() == null) {
                continue;
            }
            Long branchId = branchIdByRequestId.get(detail.getPurchaseRequestId());
            if (branchId == null) {
                continue;
            }
            quantityByBranchProduct
                    .computeIfAbsent(branchId, key -> new HashMap<>())
                    .merge(detail.getProductId(), approvedQty, Integer::sum);
        }
        if (quantityByBranchProduct.isEmpty()) {
            return List.of();
        }

        Set<Integer> productIds = quantityByBranchProduct.values().stream()
                .flatMap(map -> map.keySet().stream())
                .collect(Collectors.toSet());
        Map<Integer, ProductModel> productsById = productRepository.findByIdInWithCategory(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, product -> product, (a, b) -> a));

        Set<Long> branchIds = new HashSet<>(quantityByBranchProduct.keySet());
        Map<Long, BranchModel> branchesById = branchRepository.findAllById(branchIds).stream()
                .collect(Collectors.toMap(BranchModel::getId, branch -> branch, (a, b) -> a));

        List<ConsolidatedBranchResponse> result = new ArrayList<>();
        for (Map.Entry<Long, Map<Integer, Integer>> branchEntry : quantityByBranchProduct.entrySet()) {
            BranchModel branch = branchesById.get(branchEntry.getKey());
            ConsolidatedBranchResponse branchResponse = new ConsolidatedBranchResponse();
            branchResponse.setBranchId(branchEntry.getKey());
            branchResponse.setBranchName(branch == null ? null : branch.getName());
            branchResponse.setBranchAddress(branch == null ? null : branch.getAddress());
            branchResponse.setCategories(buildCategoryGroups(branchEntry.getValue(), productsById));
            result.add(branchResponse);
        }

        result.sort(Comparator.comparing(
                ConsolidatedBranchResponse::getBranchAddress, Comparator.nullsLast(String::compareTo)));
        return result;
    }

    @Override
    public Page<ConsolidatedBranchResponse> getConsolidatedRequestPage(PageRequestDTO pageRequest) {
        String search = pageRequest.normalizedSearch();
        String normalizedSearch = search == null ? null : search.toLowerCase(Locale.ROOT);
        List<ConsolidatedBranchResponse> filtered = getConsolidatedRequests().stream()
                .filter(branch -> normalizedSearch == null
                        || containsIgnoreCase(branch.getBranchName(), normalizedSearch)
                        || containsIgnoreCase(branch.getBranchAddress(), normalizedSearch)
                        || branch.getCategories().stream().anyMatch(category ->
                                containsIgnoreCase(category.getCategoryName(), normalizedSearch)
                                        || category.getItems().stream().anyMatch(item ->
                                        containsIgnoreCase(item.getProductCode(), normalizedSearch)
                                                || containsIgnoreCase(item.getProductName(), normalizedSearch))))
                .toList();
        Pageable pageable = pageRequest.toPageable();
        int from = Math.min((int) pageable.getOffset(), filtered.size());
        int to = Math.min(from + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(from, to), pageable, filtered.size());
    }

    private List<ConsolidatedBranchResponse.CategoryGroup> buildCategoryGroups(
            Map<Integer, Integer> quantityByProduct,
            Map<Integer, ProductModel> productsById
    ) {
        Map<Integer, ConsolidatedBranchResponse.CategoryGroup> groupByCategory = new HashMap<>();
        for (Map.Entry<Integer, Integer> productEntry : quantityByProduct.entrySet()) {
            ProductModel product = productsById.get(productEntry.getKey());
            Integer categoryId = product == null || product.getCategory() == null ? null : product.getCategory().getId();
            String categoryName = product == null || product.getCategory() == null ? null : product.getCategory().getName();

            ConsolidatedBranchResponse.CategoryGroup group = groupByCategory.computeIfAbsent(
                    categoryId == null ? -1 : categoryId,
                    key -> {
                        ConsolidatedBranchResponse.CategoryGroup created = new ConsolidatedBranchResponse.CategoryGroup();
                        created.setCategoryId(categoryId);
                        created.setCategoryName(categoryName);
                        return created;
                    });

            ConsolidatedBranchResponse.ConsolidatedItem item = new ConsolidatedBranchResponse.ConsolidatedItem();
            item.setProductId(productEntry.getKey());
            item.setProductCode(product == null ? null : product.getCode());
            item.setProductName(product == null ? null : product.getName());
            item.setUnit(product == null ? null : product.getUnit());
            item.setTotalQuantity(productEntry.getValue());
            group.getItems().add(item);
        }

        List<ConsolidatedBranchResponse.CategoryGroup> categories = new ArrayList<>(groupByCategory.values());
        for (ConsolidatedBranchResponse.CategoryGroup group : categories) {
            group.getItems().sort(Comparator.comparing(
                    ConsolidatedBranchResponse.ConsolidatedItem::getProductName, Comparator.nullsLast(String::compareTo)));
        }
        categories.sort(Comparator.comparing(
                ConsolidatedBranchResponse.CategoryGroup::getCategoryName, Comparator.nullsLast(String::compareTo)));
        return categories;
    }

    private boolean containsIgnoreCase(String value, String normalizedSearch) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedSearch);
    }

    private Long extractNumericId(String value) {
        String digits = value.replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(digits);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    @Transactional
    public PurchaseRequestResponse approveRequest(Long id, ApprovePurchaseRequestRequest request) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        assertCanApproveOrReject();
        PurchaseRequestModel purchaseRequest = findRequestOrThrow(id);
        if (purchaseRequest.getStatus() == null || !purchaseRequest.getStatus().isApprovable()) {
            throw new BadRequestException("Only pending requests can be approved.");
        }

        List<PurchaseRequestDetailModel> details = detailRepository.findByPurchaseRequestIdOrderByIdAsc(id);
        if (details.isEmpty()) {
            throw new BadRequestException("Request has no items to approve.");
        }

        Map<Integer, Integer> approvedByProduct = new HashMap<>();
        if (request != null && request.getItems() != null) {
            for (ApprovePurchaseRequestRequest.ApproveItem item : request.getItems()) {
                if (item == null || item.getProductId() == null) {
                    continue;
                }
                approvedByProduct.put(item.getProductId(), item.getApprovedQuantity());
            }
        }

        for (PurchaseRequestDetailModel detail : details) {
            Integer approved = approvedByProduct.getOrDefault(detail.getProductId(), detail.getRequestedQty());
            if (approved == null || approved < 0) {
                throw new BadRequestException("Approved quantity must be zero or greater.");
            }
            if (approved > safeStock(detail.getRequestedQty())) {
                throw new BadRequestException("Approved quantity cannot exceed requested quantity.");
            }
            detail.setApprovedQuantity(approved);
        }
        detailRepository.saveAll(details);

        // So tổng SL duyệt với tồn kho KHO TỔNG (trừ nhu cầu các yêu cầu APPROVED khác):
        //  - Đủ tất cả  -> APPROVED (đi tiếp gom đơn / chờ vận chuyển)
        //  - Thiếu bất kỳ -> AWAITING_STOCK (chờ kho tổng đặt nhà cung cấp bổ sung)
        boolean warehouseHasEnough = warehouseStockAllocationHelper.canApproveRequest(id, details);

        purchaseRequest.setStatus(warehouseHasEnough
                ? PurchaseRequestStatus.APPROVED
                : PurchaseRequestStatus.AWAITING_STOCK);
        purchaseRequest.setApprovedBy(currentUser.getId());
        purchaseRequest.setApprovedAt(LocalDateTime.now());
        purchaseRequest.setRejectReason(null);
        PurchaseRequestModel saved = purchaseRequestRepository.save(purchaseRequest);
        warehouseStockAllocationHelper.reconcileApprovedStockStatus();
        return buildResponse(saved);
    }

    private Map<Integer, Integer> loadWarehouseStock(List<PurchaseRequestDetailModel> details) {
        Set<Integer> productIds = details.stream()
                .map(PurchaseRequestDetailModel::getProductId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Integer, Integer> stockByProduct = new HashMap<>();
        if (productIds.isEmpty()) {
            return stockByProduct;
        }
        for (WarehouseInventoryModel inventory : warehouseInventoryRepository.findByProductIdIn(productIds)) {
            stockByProduct.put(inventory.getProductId(), safeStock(inventory.getQuantity()));
        }
        return stockByProduct;
    }

    private boolean hasEnoughWarehouseStock(
            List<PurchaseRequestDetailModel> details,
            Map<Integer, Integer> warehouseStockByProduct
    ) {
        for (PurchaseRequestDetailModel detail : details) {
            int approved = safeStock(detail.getApprovedQuantity());
            if (approved <= 0) {
                continue;
            }
            int available = warehouseStockByProduct.getOrDefault(detail.getProductId(), 0);
            if (approved > available) {
                return false;
            }
        }
        return true;
    }

    @Override
    @Transactional
    public PurchaseRequestResponse rejectRequest(Long id, RejectPurchaseRequestRequest request) {
        throw new BadRequestException("Rejecting purchase requests is not supported.");
    }

    @Override
    @Transactional
    public PurchaseRequestResponse receiveGoods(Long id, ReceiveGoodsRequest request) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        PurchaseRequestModel purchaseRequest = findRequestOrThrow(id);
        assertCanReceive(purchaseRequest, currentUser);
        if (purchaseRequest.getStatus() == null || !purchaseRequest.getStatus().isReceivable()) {
            throw new BadRequestException(
                    "Direct receive is disabled. Use Order Tracking to receive shipments in transit.");
        }

        List<PurchaseRequestDetailModel> details = detailRepository.findByPurchaseRequestIdOrderByIdAsc(id);
        if (details.isEmpty()) {
            throw new BadRequestException("Request has no items to receive.");
        }

        Map<Integer, Integer> receivedByProduct = new HashMap<>();
        if (request != null && request.getItems() != null) {
            for (ReceiveGoodsRequest.ReceiveItem item : request.getItems()) {
                if (item == null || item.getProductId() == null) {
                    continue;
                }
                receivedByProduct.put(item.getProductId(), item.getReceivedQuantity());
            }
        }

        GoodsReceiptModel receipt = new GoodsReceiptModel();
        receipt.setPurchaseRequestId(purchaseRequest.getId());
        receipt.setBranchId(purchaseRequest.getBranchId());
        receipt.setStockStaffId(currentUser.getId());
        receipt.setStatus("completed");
        GoodsReceiptModel savedReceipt = goodsReceiptRepository.save(receipt);

        Map<Integer, ProductModel> productsById = loadProductsById(details);

        // requestedQty / approvedQuantity / receivedQuantity are all expressed in TOP packaging
        // units (e.g. cases). Stock ledgers (branch_inventory) are kept in BASE units, so every
        // quantity is converted via ProductPackagingService before it touches branch stock.
        List<GoodsReceiptItemModel> receiptItems = new ArrayList<>();
        for (PurchaseRequestDetailModel detail : details) {
            ProductModel product = productsById.get(detail.getProductId());
            int orderedTopUnits = detail.getApprovedQuantity() != null
                    ? detail.getApprovedQuantity()
                    : safeStock(detail.getRequestedQty());
            Integer receivedTopUnitsInput = receivedByProduct.getOrDefault(detail.getProductId(), orderedTopUnits);
            if (receivedTopUnitsInput == null || receivedTopUnitsInput < 0) {
                throw new BadRequestException("Received quantity must be zero or greater.");
            }
            int receivedTopUnits = receivedTopUnitsInput;

            int orderedBaseUnits = productPackagingService.toBaseQty(orderedTopUnits, product);
            int receivedBaseUnits = productPackagingService.toBaseQty(receivedTopUnits, product);

            GoodsReceiptItemModel receiptItem = new GoodsReceiptItemModel();
            receiptItem.setGoodsReceiptId(savedReceipt.getId());
            receiptItem.setProductId(detail.getProductId());
            receiptItem.setOrderedQuantity(orderedBaseUnits);
            receiptItem.setReceivedQuantity(receivedBaseUnits);
            receiptItems.add(receiptItem);

            increaseBranchStock(purchaseRequest.getBranchId(), detail.getProductId(), receivedBaseUnits);
        }
        goodsReceiptItemRepository.saveAll(receiptItems);

        purchaseRequest.setStatus(PurchaseRequestStatus.RECEIVED);
        return buildResponse(purchaseRequestRepository.save(purchaseRequest));
    }

    private PurchaseRequestModel findRequestOrThrow(Long id) {
        return purchaseRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Request not found."));
    }

    private Long resolveBranchManagerBranchId(UserModel currentUser) {
        UserRole role = currentUserProvider.getCurrentUserRole();
        if (role != UserRole.BRANCH_MANAGER || currentUser.getBranchId() == null) {
            throw new ForbiddenException("Access denied.");
        }
        return currentUser.getBranchId();
    }

    private Long resolveBranchStaffBranchId(UserModel currentUser) {
        UserRole role = currentUserProvider.getCurrentUserRole();
        if (role != UserRole.INVENTORY_STAFF || currentUser.getBranchId() == null) {
            throw new ForbiddenException("Access denied.");
        }
        return currentUser.getBranchId();
    }

    private void assertCanViewRequest(PurchaseRequestModel request, UserModel currentUser) {
        UserRole role = currentUserProvider.getCurrentUserRole();
        if (role == UserRole.ADMIN || role == UserRole.DIRECTOR) {
            return;
        }
        if (role == UserRole.WAREHOUSE_MANAGER
                && request.getStatus() != null
                && request.getStatus().isWarehouseVisible()) {
            return;
        }
        if (role == UserRole.BRANCH_MANAGER
                && currentUser.getBranchId() != null
                && currentUser.getBranchId().equals(request.getBranchId())) {
            return;
        }
        if (role == UserRole.INVENTORY_STAFF
                && currentUser.getBranchId() != null
                && currentUser.getBranchId().equals(request.getBranchId())) {
            return;
        }
        throw new ForbiddenException("Access denied.");
    }

    private void assertDraftEditable(PurchaseRequestModel request) {
        if (request.getStatus() == PurchaseRequestStatus.PENDING) {
            throw new ForbiddenException("Request already submitted.");
        }
        if (request.getStatus() == null || !request.getStatus().isEditable()) {
            throw new ForbiddenException("Cannot edit submitted request.");
        }
    }

    private void assertCanApproveOrReject() {
        UserRole role = currentUserProvider.getCurrentUserRole();
        if (role == UserRole.WAREHOUSE_MANAGER) {
            return;
        }
        throw new ForbiddenException("Access denied.");
    }

    private void assertCanReceive(PurchaseRequestModel request, UserModel currentUser) {
        UserRole role = currentUserProvider.getCurrentUserRole();
        if (role == UserRole.INVENTORY_STAFF
                && currentUser.getBranchId() != null
                && currentUser.getBranchId().equals(request.getBranchId())) {
            return;
        }
        throw new ForbiddenException("Access denied.");
    }

    private void increaseBranchStock(Long branchId, Integer productId, int quantity) {
        if (quantity <= 0) {
            return;
        }
        BranchInventoryModel inventory = branchInventoryRepository
                .findByBranchIdAndProductId(branchId, productId)
                .orElseGet(() -> {
                    BranchInventoryModel created = new BranchInventoryModel();
                    created.setBranchId(branchId);
                    created.setProductId(productId);
                    created.setCurrentStock(0);
                    return created;
                });
        inventory.setCurrentStock(safeStock(inventory.getCurrentStock()) + quantity);
        branchInventoryRepository.save(inventory);
    }

    private Map<Integer, Integer> buildRequestedQuantities(
            List<PurchaseRequestItemRequest> items,
            Boolean addAllRecommended,
            Long branchId
    ) {
        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        if (items != null) {
            for (PurchaseRequestItemRequest item : items) {
                if (item == null) {
                    continue;
                }
                Integer productId = item.getProductId();
                Integer requestedQty = toRequestedQuantity(item.getRequestedQty());
                addQuantity(quantities, productId, requestedQty);
            }
        }

        if (Boolean.TRUE.equals(addAllRecommended)) {
            for (RecommendedProductResponse recommendedProduct : getRecommendedProductsForBranch(branchId)) {
                if (recommendedProduct.getSuggestedQty() != null && recommendedProduct.getSuggestedQty() > 0) {
                    addQuantity(quantities, recommendedProduct.getProductId(), recommendedProduct.getSuggestedQty());
                }
            }
        }

        return quantities;
    }

    private void addQuantity(Map<Integer, Integer> quantities, Integer productId, Integer requestedQty) {
        if (productId == null) {
            throw new BadRequestException("Product is required.");
        }
        if (requestedQty == null || requestedQty <= 0) {
            throw new BadRequestException("Quantity must be greater than zero.");
        }
        quantities.merge(productId, requestedQty, Integer::sum);
    }

    private Integer toRequestedQuantity(BigDecimal requestedQty) {
        if (requestedQty == null) {
            throw new BadRequestException("Quantity is required.");
        }
        try {
            if (requestedQty.stripTrailingZeros().scale() > 0) {
                throw new ArithmeticException("decimal");
            }
            int value = requestedQty.intValueExact();
            if (value <= 0) {
                throw new BadRequestException("Quantity must be greater than zero.");
            }
            return value;
        } catch (ArithmeticException ex) {
            throw new BadRequestException("Quantity must be an integer.");
        }
    }

    private void replaceDetails(Long requestId, Map<Integer, Integer> quantities) {
        detailRepository.deleteByPurchaseRequestId(requestId);
        if (quantities.isEmpty()) {
            return;
        }

        Map<Integer, ProductModel> productsById = productRepository.findByIdInWithCategory(quantities.keySet())
                .stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity(), (a, b) -> a));

        List<PurchaseRequestDetailModel> details = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
            ProductModel product = productsById.get(entry.getKey());
            if (product == null || !"active".equalsIgnoreCase(product.getStatus())) {
                throw new NotFoundException("Product not found.");
            }
            details.add(purchaseRequestMapper.buildDetailSnapshot(requestId, product, entry.getValue()));
        }
        detailRepository.saveAll(details);
    }

    private List<RecommendedProductResponse> getRecommendedProductsForBranch(Long branchId) {
        // Scope A: all active products visible to the branch; missing inventory row = stock 0.
        List<ProductModel> products = productRepository.findVisibleActiveProducts(false, branchId);
        if (products.isEmpty()) {
            return List.of();
        }

        Set<Integer> productIds = products.stream()
                .map(ProductModel::getId)
                .collect(Collectors.toSet());
        Map<Integer, BranchInventoryModel> inventoryByProductId = new HashMap<>();
        for (BranchInventoryModel row : branchInventoryRepository.findByBranchIdAndProductIdIn(
                branchId, productIds)) {
            inventoryByProductId.put(row.getProductId(), row);
        }
        Map<Integer, ProductPackagingModel> topPackagings =
                productPackagingService.getTopPackagingsByProductIds(productIds);

        Map<Integer, Integer> soldByProduct = new HashMap<>();
        for (Object[] row : orderItemRepository.sumSoldQuantityByProduct(branchId, LocalDateTime.now().minusDays(30))) {
            if (row[0] instanceof Number productId && row[1] instanceof Number sold) {
                soldByProduct.put(productId.intValue(), sold.intValue());
            }
        }

        record Candidate(RecommendedProductResponse response, boolean lowStock, int sold30, int shortfallBaseUnits) {}

        List<Candidate> candidates = new ArrayList<>();
        for (ProductModel product : products) {
            BranchInventoryModel inventory = inventoryByProductId.get(product.getId());
            int currentStock = inventory == null ? 0 : safeStock(inventory.getCurrentStock());
            int reorderPoint = resolveBranchReorderPoint(product, inventory);
            int sold30 = soldByProduct.getOrDefault(product.getId(), 0);
            boolean lowStock = reorderPoint > 0 && currentStock <= reorderPoint;
            boolean highSellThrough = sold30 > currentStock;
            if (!lowStock && !highSellThrough) {
                continue;
            }
            int targetStock = Math.max(reorderPoint, sold30);
            int shortfallBaseUnits = Math.max(targetStock - currentStock, 0);
            ProductPackagingModel top = topPackagings.get(product.getId());
            if (top == null) {
                top = productPackagingService.getTopPackaging(product);
            }
            int conversionQty = productPackagingService.conversionQtyOf(top);
            int suggestedQty = toTopUnitsCeil(shortfallBaseUnits, conversionQty);
            RecommendedProductResponse recommendation = purchaseRequestMapper.toRecommendedProductResponse(
                            product,
                            currentStock,
                            reorderPoint,
                            suggestedQty,
                            top);
            recommendation.setSoldLast30Days(sold30);
            recommendation.setPriorityReason(lowStock && highSellThrough
                    ? "Low stock + high sell-through"
                    : lowStock ? "Low stock" : "High sell-through");
            candidates.add(new Candidate(recommendation, lowStock, sold30, shortfallBaseUnits));
        }
        candidates.sort(Comparator
                .comparing(Candidate::lowStock).reversed()
                .thenComparing(Comparator.comparingInt(Candidate::sold30).reversed())
                .thenComparing(Comparator.comparingInt(Candidate::shortfallBaseUnits).reversed())
                .thenComparing(
                        c -> c.response().getProductName(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return candidates.stream()
                .limit(MAX_RECOMMENDED_PRODUCTS)
                .map(Candidate::response)
                .toList();
    }

    private int resolveBranchReorderPoint(ProductModel product, BranchInventoryModel inventory) {
        // Category-only policy: ignore per-SKU branch_inventory.reorder_point overrides.
        String categoryName = product.getCategory() == null ? null : product.getCategory().getName();
        int fromRule = CategoryReorderPoints.forBranch(categoryName, product.getUnitsPerImportUnit());
        if (fromRule > 0) {
            return fromRule;
        }
        return defaultReorderPoint == null ? 0 : defaultReorderPoint;
    }

    private int safeStock(Integer stock) {
        return stock == null ? 0 : stock;
    }

    /** Convert a BASE-unit shortfall into a TOP packaging quantity (rounded up), minimum 1 when > 0. */
    private int toTopUnitsCeil(int shortfallBaseUnits, int conversionQty) {
        if (shortfallBaseUnits <= 0) {
            return 0;
        }
        int qty = conversionQty < 1 ? 1 : conversionQty;
        return Math.max(1, (shortfallBaseUnits + qty - 1) / qty);
    }

    private PurchaseRequestResponse buildResponse(PurchaseRequestModel request) {
        BranchModel branch = branchRepository.findById(request.getBranchId()).orElse(null);
        UserModel createdBy = userRepository.findById(request.getCreatedBy()).orElse(null);
        UserModel approvedBy = request.getApprovedBy() == null
                ? null
                : userRepository.findById(request.getApprovedBy()).orElse(null);
        List<PurchaseRequestDetailModel> details = detailRepository.findByPurchaseRequestIdOrderByIdAsc(request.getId());
        Map<Integer, ProductModel> productsById = loadProductsById(details);
        Map<Integer, Integer> warehouseStockByProduct = loadWarehouseStock(details);
        Map<Integer, ProductPackagingModel> topPackagingsByProduct = new HashMap<>(
                productPackagingService.getTopPackagingsByProductIds(productsById.keySet()));
        // Fill any products missing a purchase-default row once (legacy fallback), avoid per-line DB in mapper.
        for (ProductModel product : productsById.values()) {
            if (!topPackagingsByProduct.containsKey(product.getId())) {
                ProductPackagingModel top = productPackagingService.getTopPackaging(product);
                if (top != null) {
                    topPackagingsByProduct.put(product.getId(), top);
                }
            }
        }
        return purchaseRequestMapper.toResponse(
                request,
                branch,
                createdBy,
                approvedBy,
                details,
                productsById,
                warehouseStockByProduct,
                topPackagingsByProduct);
    }

    private PurchaseRequestSummaryResponse buildSummaryResponse(PurchaseRequestModel request) {
        BranchModel branch = branchRepository.findById(request.getBranchId()).orElse(null);
        UserModel createdBy = userRepository.findById(request.getCreatedBy()).orElse(null);
        int itemCount = (int) detailRepository.countByPurchaseRequestId(request.getId());
        return buildSummaryResponse(request, branch, createdBy, itemCount);
    }

    private PurchaseRequestSummaryResponse buildSummaryResponse(
            PurchaseRequestModel request,
            BranchModel branch,
            UserModel createdBy,
            int itemCount) {
        return purchaseRequestMapper.toSummaryResponse(request, itemCount, branch, createdBy);
    }

    private Pageable newestFirst(PageRequestDTO pageRequest) {
        PageRequestDTO safeRequest = pageRequest == null ? new PageRequestDTO() : pageRequest;
        return safeRequest.toPageable(
                "createdAt",
                Sort.Direction.DESC,
                Set.of("id", "status", "createdAt", "approvedAt"));
    }

    private Pageable productSearchPage(PageRequestDTO pageRequest) {
        PageRequestDTO safeRequest = pageRequest == null ? new PageRequestDTO() : pageRequest;
        return safeRequest.toPageable("name", Sort.Direction.ASC, Set.of("id", "code", "name"));
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private Map<Integer, ProductModel> loadProductsById(List<PurchaseRequestDetailModel> details) {
        if (details == null || details.isEmpty()) {
            return Map.of();
        }
        Set<Integer> productIds = details.stream()
                .map(PurchaseRequestDetailModel::getProductId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findByIdInWithCategory(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity(), (a, b) -> a));
    }
}
