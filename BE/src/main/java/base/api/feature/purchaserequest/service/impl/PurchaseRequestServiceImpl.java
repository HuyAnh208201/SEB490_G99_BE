package base.api.feature.purchaserequest.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.product.repository.IProductRepository;
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
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PurchaseRequestServiceImpl implements IPurchaseRequestService {

    private static final Set<PurchaseRequestStatus> WAREHOUSE_VISIBLE_STATUSES = EnumSet.of(
            PurchaseRequestStatus.PENDING,
            PurchaseRequestStatus.APPROVED,
            PurchaseRequestStatus.AWAITING_STOCK,
            PurchaseRequestStatus.RECEIVED
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
    private IBranchRepository branchRepository;

    @Autowired
    private IUserRepository userRepository;

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

        purchaseRequest.setStatus(PurchaseRequestStatus.PENDING);
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
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole role = currentUserProvider.getCurrentUserRole();
        Pageable pageable = newestFirst(pageRequest);

        Page<PurchaseRequestModel> requests;
        if (role == UserRole.ADMIN || role == UserRole.DIRECTOR) {
            requests = purchaseRequestRepository.findAll(pageable);
        } else if (role == UserRole.WAREHOUSE_MANAGER) {
            requests = purchaseRequestRepository.findByStatusIn(WAREHOUSE_VISIBLE_STATUSES, pageable);
        } else if (role == UserRole.BRANCH_MANAGER) {
            Long branchId = resolveBranchManagerBranchId(currentUser);
            requests = purchaseRequestRepository.findByBranchId(branchId, pageable);
        } else if (role == UserRole.INVENTORY_STAFF) {
            Long branchId = resolveBranchStaffBranchId(currentUser);
            requests = purchaseRequestRepository.findByBranchId(branchId, pageable);
        } else {
            throw new ForbiddenException("Access denied.");
        }

        List<PurchaseRequestModel> content = requests.getContent();
        Set<Long> branchIds = content.stream().map(PurchaseRequestModel::getBranchId).collect(Collectors.toSet());
        Set<Long> userIds = content.stream().map(PurchaseRequestModel::getCreatedBy).collect(Collectors.toSet());
        Map<Long, BranchModel> branchesById = branchRepository.findAllById(branchIds).stream()
                .collect(Collectors.toMap(BranchModel::getId, Function.identity(), (a, b) -> a));
        Map<Long, UserModel> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserModel::getId, Function.identity(), (a, b) -> a));

        return requests.map(request -> buildSummaryResponse(
                request,
                branchesById.get(request.getBranchId()),
                usersById.get(request.getCreatedBy())));
    }

    @Override
    public List<RecommendedProductResponse> getRecommendedProducts() {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        Long branchId = resolveBranchManagerBranchId(currentUser);
        return getRecommendedProductsForBranch(branchId);
    }

    @Override
    public Page<ProductSearchResponse> searchProducts(String keyword, PageRequestDTO pageRequest) {
        return productRepository.searchActiveProducts(normalizeNullableText(keyword), productSearchPage(pageRequest))
                .map(purchaseRequestMapper::toProductSearchResponse);
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
        return buildResponse(purchaseRequestRepository.save(purchaseRequest));
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
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        assertCanApproveOrReject();
        PurchaseRequestModel purchaseRequest = findRequestOrThrow(id);
        if (purchaseRequest.getStatus() == null || !purchaseRequest.getStatus().isApprovable()) {
            throw new BadRequestException("Only pending requests can be rejected.");
        }

        String reason = request == null ? null : normalizeNullableText(request.getReason());
        if (reason == null) {
            throw new BadRequestException("Reject reason is required.");
        }

        purchaseRequest.setStatus(PurchaseRequestStatus.REJECTED);
        purchaseRequest.setRejectReason(reason);
        purchaseRequest.setApprovedBy(currentUser.getId());
        purchaseRequest.setApprovedAt(LocalDateTime.now());
        return buildResponse(purchaseRequestRepository.save(purchaseRequest));
    }

    @Override
    @Transactional
    public PurchaseRequestResponse receiveGoods(Long id, ReceiveGoodsRequest request) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        PurchaseRequestModel purchaseRequest = findRequestOrThrow(id);
        assertCanReceive(purchaseRequest, currentUser);
        if (purchaseRequest.getStatus() == null || !purchaseRequest.getStatus().isReceivable()) {
            throw new BadRequestException("Only approved requests can be received.");
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

        List<GoodsReceiptItemModel> receiptItems = new ArrayList<>();
        for (PurchaseRequestDetailModel detail : details) {
            int ordered = detail.getApprovedQuantity() != null
                    ? detail.getApprovedQuantity()
                    : safeStock(detail.getRequestedQty());
            Integer receivedInput = receivedByProduct.getOrDefault(detail.getProductId(), ordered);
            if (receivedInput == null || receivedInput < 0) {
                throw new BadRequestException("Received quantity must be zero or greater.");
            }
            int received = receivedInput;

            GoodsReceiptItemModel receiptItem = new GoodsReceiptItemModel();
            receiptItem.setGoodsReceiptId(savedReceipt.getId());
            receiptItem.setProductId(detail.getProductId());
            receiptItem.setOrderedQuantity(ordered);
            receiptItem.setReceivedQuantity(received);
            receiptItems.add(receiptItem);

            increaseBranchStock(purchaseRequest.getBranchId(), detail.getProductId(), received);
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
        if (role == UserRole.ADMIN
                || role == UserRole.DIRECTOR
                || role == UserRole.WAREHOUSE_MANAGER) {
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

        List<PurchaseRequestDetailModel> details = quantities.entrySet().stream()
                .map(entry -> {
                    ProductModel product = resolveActiveProduct(entry.getKey());
                    return purchaseRequestMapper.buildDetailSnapshot(requestId, product, entry.getValue());
                })
                .toList();
        detailRepository.saveAll(details);
    }

    private ProductModel resolveActiveProduct(Integer productId) {
        ProductModel product = productRepository.findByIdWithCategory(productId)
                .orElseThrow(() -> new NotFoundException("Product not found."));
        if (!"active".equalsIgnoreCase(product.getStatus())) {
            throw new NotFoundException("Product not found.");
        }
        return product;
    }

    private List<RecommendedProductResponse> getRecommendedProductsForBranch(Long branchId) {
        Map<Integer, Integer> currentStockByProductId = new HashMap<>();
        for (BranchInventoryModel inventory : branchInventoryRepository.findByBranchId(branchId)) {
            currentStockByProductId.put(inventory.getProductId(), safeStock(inventory.getCurrentStock()));
        }

        // TODO: load reorder point from branch_inventory / product master when that field exists.
        int reorderPoint = defaultReorderPoint == null ? 0 : defaultReorderPoint;

        return productRepository.findAllActiveProducts().stream()
                .map(product -> {
                    int currentStock = currentStockByProductId.getOrDefault(product.getId(), 0);
                    int suggestedQty = Math.max(reorderPoint - currentStock, 0);
                    return purchaseRequestMapper.toRecommendedProductResponse(product, currentStock, reorderPoint, suggestedQty);
                })
                .filter(product -> product.getCurrentStock() <= product.getReorderPoint())
                .toList();
    }

    private int safeStock(Integer stock) {
        return stock == null ? 0 : stock;
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
        return purchaseRequestMapper.toResponse(
                request, branch, createdBy, approvedBy, details, productsById, warehouseStockByProduct);
    }

    private PurchaseRequestSummaryResponse buildSummaryResponse(PurchaseRequestModel request) {
        BranchModel branch = branchRepository.findById(request.getBranchId()).orElse(null);
        UserModel createdBy = userRepository.findById(request.getCreatedBy()).orElse(null);
        return buildSummaryResponse(request, branch, createdBy);
    }

    private PurchaseRequestSummaryResponse buildSummaryResponse(
            PurchaseRequestModel request,
            BranchModel branch,
            UserModel createdBy) {
        int itemCount = (int) detailRepository.countByPurchaseRequestId(request.getId());
        return purchaseRequestMapper.toSummaryResponse(request, itemCount, branch, createdBy);
    }

    private Pageable newestFirst(PageRequestDTO pageRequest) {
        PageRequestDTO safeRequest = pageRequest == null ? new PageRequestDTO() : pageRequest;
        return PageRequest.of(
                Math.max(0, safeRequest.getPage() - 1),
                Math.max(1, safeRequest.getSize()),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
    }

    private Pageable productSearchPage(PageRequestDTO pageRequest) {
        PageRequestDTO safeRequest = pageRequest == null ? new PageRequestDTO() : pageRequest;
        return PageRequest.of(
                Math.max(0, safeRequest.getPage() - 1),
                Math.max(1, safeRequest.getSize()),
                Sort.by(Sort.Direction.ASC, "name")
        );
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private Map<Integer, ProductModel> loadProductsById(List<PurchaseRequestDetailModel> details) {
        Map<Integer, ProductModel> productsById = new HashMap<>();
        for (PurchaseRequestDetailModel detail : details) {
            if (detail.getProductId() == null || productsById.containsKey(detail.getProductId())) {
                continue;
            }
            productRepository.findByIdWithCategory(detail.getProductId())
                    .ifPresent(product -> productsById.put(product.getId(), product));
        }
        return productsById;
    }
}
