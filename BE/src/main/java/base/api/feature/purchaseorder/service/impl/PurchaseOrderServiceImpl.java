package base.api.feature.purchaseorder.service.impl;

import base.api.feature.dispatch.service.WarehouseStockAllocationHelper;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductCostService;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.purchaseorder.dto.request.CreatePurchaseOrderRequest;
import base.api.feature.purchaseorder.dto.response.PurchaseOrderResponse;
import base.api.feature.purchaseorder.dto.response.PurchaseProductOptionResponse;
import base.api.feature.purchaseorder.dto.response.RecommendedPurchaseProductResponse;
import base.api.feature.purchaseorder.mapper.PurchaseOrderMapper;
import base.api.feature.purchaseorder.repository.PurchaseOrderItemRepository;
import base.api.feature.purchaseorder.repository.PurchaseOrderRepository;
import base.api.feature.purchaseorder.service.IPurchaseOrderService;
import base.api.feature.purchaserequest.repository.PurchaseRequestDetailRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.feature.supplier.repository.ISupplierRepository;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.ProductPackagingModel;
import base.api.shared.entity.PurchaseOrderItemModel;
import base.api.shared.entity.PurchaseOrderModel;
import base.api.shared.entity.PurchaseRequestDetailModel;
import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.entity.SupplierModel;
import base.api.shared.entity.WarehouseInventoryModel;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.enums.PurchaseOrderStatus;
import base.api.shared.enums.PurchaseRequestStatus;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Locale;

@Service
public class PurchaseOrderServiceImpl implements IPurchaseOrderService {

    private static final int SEARCH_LIMIT = 20;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private PurchaseOrderItemRepository purchaseOrderItemRepository;

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private PurchaseRequestDetailRepository detailRepository;

    @Autowired
    private WarehouseInventoryRepository warehouseInventoryRepository;

    @Autowired
    private IProductRepository productRepository;

    @Autowired
    private ISupplierRepository supplierRepository;

    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private WarehouseStockAllocationHelper warehouseStockAllocationHelper;

    @Autowired
    private ProductPackagingService productPackagingService;

    @Autowired
    private ProductCostService productCostService;

    @Override
    public List<RecommendedPurchaseProductResponse> getRecommendedProducts() {
        Map<Integer, Integer> demandByProduct = demandForWarehouseShortfall();

        Set<Integer> productIds = new LinkedHashSet<>(demandByProduct.keySet());
        for (WarehouseInventoryModel inv : warehouseInventoryRepository.findBelowReorderPoint()) {
            productIds.add(inv.getProductId());
        }
        if (productIds.isEmpty()) {
            return List.of();
        }

        Map<Integer, WarehouseInventoryModel> stockByProduct = warehouseInventoryRepository
                .findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(WarehouseInventoryModel::getProductId, inv -> inv, (a, b) -> a));

        Map<Integer, ProductModel> productsById = productRepository.findByIdInWithCategory(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, p -> p, (a, b) -> a));
        Map<Integer, ProductPackagingModel> topPackagings = loadTopPackagings(productsById);

        List<RecommendedPurchaseProductResponse> result = new ArrayList<>();
        for (Integer productId : productIds) {
            ProductModel product = productsById.get(productId);
            if (product == null) {
                continue;
            }
            WarehouseInventoryModel inv = stockByProduct.get(productId);
            int currentBase = inv == null ? 0 : safe(inv.getQuantity());
            int reorderBase = inv == null ? 0 : safe(inv.getReorderPoint());
            int demandBase = demandByProduct.getOrDefault(productId, 0);
            int requiredBase = Math.max(demandBase, reorderBase);
            int suggestedBase = Math.max(requiredBase - currentBase, 0);
            if (suggestedBase <= 0) {
                continue;
            }

            ProductPackagingModel top = topPackagings.get(productId);
            int conversion = productPackagingService.conversionQtyOf(top);
            int requiredTop = toTopUnits(requiredBase, conversion);
            int suggestedTop = toTopUnits(suggestedBase, conversion);
            if (suggestedTop <= 0) {
                continue;
            }

            String topLabel = top == null ? null : top.displayLabel();
            RecommendedPurchaseProductResponse row = new RecommendedPurchaseProductResponse();
            row.setProductId(productId);
            row.setProductCode(product.getCode());
            row.setProductName(product.getName());
            row.setCategoryName(product.getCategory() == null ? null : product.getCategory().getName());
            row.setUnit(topLabel);
            row.setTopPackagingLabel(topLabel);
            row.setCurrentQty(currentBase);
            row.setCurrentQtyBase(currentBase);
            row.setRequiredQty(requiredTop);
            row.setRequiredQtyBase(requiredBase);
            row.setSuggestedQty(suggestedTop);
            row.setSuggestedQtyBase(suggestedBase);
            row.setReferencePrice(product.getReferenceImportPrice());
            result.add(row);
        }
        result.sort(Comparator.comparing(
                RecommendedPurchaseProductResponse::getSuggestedQty, Comparator.reverseOrder()));
        return result;
    }

    @Override
    public List<PurchaseProductOptionResponse> searchProducts(Integer ignoredSupplierId, String keyword) {
        String looseKeyword = base.api.shared.util.ProductSearchNormalizer.toLooseLikePattern(normalize(keyword));
        List<ProductModel> products = productRepository
                .searchActiveProducts(
                        looseKeyword,
                        PageRequest.of(0, SEARCH_LIMIT, Sort.by(Sort.Direction.ASC, "name")))
                .getContent();
        Set<Integer> productIds = products.stream().map(ProductModel::getId).collect(Collectors.toSet());
        Map<Integer, Integer> stockByProduct = productIds.isEmpty()
                ? Map.of()
                : warehouseInventoryRepository.findByProductIdIn(productIds).stream()
                        .collect(Collectors.toMap(
                                WarehouseInventoryModel::getProductId,
                                inv -> safe(inv.getQuantity()),
                                (a, b) -> a));
        return products.stream()
                .map(product -> {
                    PurchaseProductOptionResponse row = new PurchaseProductOptionResponse();
                    row.setProductId(product.getId());
                    row.setProductCode(product.getCode());
                    row.setProductName(product.getName());
                    row.setCategoryName(product.getCategory() == null ? null : product.getCategory().getName());
                    row.setUnit(product.getUnit());
                    row.setImportUnit(product.getImportUnit());
                    row.setConversionQty(product.getUnitsPerImportUnit());
                    row.setTopPackagingLabel(product.getImportUnit() == null ? null
                            : product.getImportUnit() + " of " + Math.max(1, safe(product.getUnitsPerImportUnit())));
                    row.setCurrentQty(stockByProduct.getOrDefault(product.getId(), 0));
                    row.setReferencePrice(product.getReferenceImportPrice());
                    return row;
                })
                .toList();
    }

    @Override
    @Transactional
    public PurchaseOrderResponse createOrder(CreatePurchaseOrderRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("At least one product must be added.");
        }
        SupplierModel supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new NotFoundException("Supplier not found."));

        // Gộp số lượng theo sản phẩm (tránh trùng dòng).
        Map<Integer, Integer> quantityByProduct = new LinkedHashMap<>();
        Map<Integer, BigDecimal> priceByProduct = new HashMap<>();
        for (CreatePurchaseOrderRequest.Item item : request.getItems()) {
            if (item == null || item.getProductId() == null) {
                throw new BadRequestException("Product is required.");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BadRequestException("Quantity must be greater than zero.");
            }
            quantityByProduct.merge(item.getProductId(), item.getQuantity(), Integer::sum);
            if (item.getUnitPrice() != null) {
                priceByProduct.putIfAbsent(item.getProductId(), item.getUnitPrice());
            }
        }

        Map<Integer, ProductModel> productsById = productRepository.findByIdInWithCategory(quantityByProduct.keySet()).stream()
                .collect(Collectors.toMap(ProductModel::getId, p -> p, (a, b) -> a));
        for (Integer productId : quantityByProduct.keySet()) {
            if (!productsById.containsKey(productId)) {
                throw new NotFoundException("Product not found: " + productId);
            }
        }

        base.api.shared.entity.UserModel receiver = currentUserProvider.getCurrentUserOrThrow();
        PurchaseOrderModel order = new PurchaseOrderModel();
        order.setSupplierId(supplier.getId());
        order.setStatus(PurchaseOrderStatus.RECEIVED);
        order.setNotes(normalize(request.getNotes()));
        order.setSupplierDeliveryDate(request.getSupplierDeliveryDate());
        order.setDeliveredByName(normalize(request.getDeliveredByName()));
        order.setDeliveredByPhone(normalize(request.getDeliveredByPhone()));
        order.setSupplierDocumentNumber(normalize(request.getSupplierDocumentNumber()));
        order.setCreatedBy(receiver.getId());
        order.setReceivedBy(receiver.getId());
        order.setReceivedByName(receiver.getFullName());
        order.setReceivedByPhone(receiver.getPhone());
        order.setReceivedAt(LocalDateTime.now());
        PurchaseOrderModel savedOrder = purchaseOrderRepository.save(order);

        List<PurchaseOrderItemModel> items = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : quantityByProduct.entrySet()) {
            ProductModel product = productsById.get(entry.getKey());
            PurchaseOrderItemModel item = new PurchaseOrderItemModel();
            item.setPurchaseOrderId(savedOrder.getId());
            item.setProductId(entry.getKey());
            item.setQuantity(entry.getValue());
            item.setUnitPrice(priceByProduct.getOrDefault(entry.getKey(), product.getReferenceImportPrice()));
            items.add(item);
            if (item.getUnitPrice() != null) {
                product.setReferenceImportPrice(
                        productCostService.baseUnitCostFromPackPrice(item.getUnitPrice(), product));
            }
        }
        purchaseOrderItemRepository.saveAll(items);
        productRepository.saveAll(productsById.values());

        Map<Integer, Integer> baseQtyByProduct = new HashMap<>();
        quantityByProduct.forEach((productId, quantity) -> {
            int baseQty = productPackagingService.toBaseQty(quantity, productsById.get(productId));
            if (baseQty > 0) {
                baseQtyByProduct.put(productId, baseQty);
            }
        });
        increaseWarehouseStockBatch(baseQtyByProduct);
        reevaluateAwaitingStock();

        return buildDetail(savedOrder);
    }

    @Override
    public List<PurchaseOrderResponse> getOrders() {
        return buildSummaries(purchaseOrderRepository.findAllByOrderByCreatedAtDesc());
    }

    @Override
    public Page<PurchaseOrderResponse> getOrderPage(
            PageRequestDTO pageRequest,
            PurchaseOrderStatus status
    ) {
        PageRequestDTO query = pageRequest == null ? new PageRequestDTO() : pageRequest;
        Specification<PurchaseOrderModel> specification = (root, ignored, cb) -> cb.conjunction();
        if (status != null) {
            specification = specification.and((root, ignored, cb) -> cb.equal(root.get("status"), status));
        }
        String search = query.normalizedSearch();
        if (search != null) {
            String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
            Long id = parseOrderIdentifier(search);
            Set<Integer> supplierIds = supplierRepository.findAll((root, ignored, cb) ->
                            cb.like(cb.lower(root.get("name")), pattern)).stream()
                    .map(SupplierModel::getId)
                    .collect(Collectors.toSet());
            specification = specification.and((root, ignored, cb) -> cb.or(
                    cb.like(cb.lower(root.get("notes")), pattern),
                    id == null ? cb.disjunction() : cb.equal(root.get("id"), id),
                    supplierIds.isEmpty() ? cb.disjunction() : root.get("supplierId").in(supplierIds)
            ));
        }
        Page<PurchaseOrderModel> orders = purchaseOrderRepository.findAll(
                specification,
                query.toPageable(
                        "createdAt",
                        Sort.Direction.DESC,
                        Set.of("id", "status", "supplierId", "createdAt", "receivedAt", "updatedAt")));
        return new PageImpl<>(
                buildSummaries(orders.getContent()),
                orders.getPageable(),
                orders.getTotalElements());
    }

    private Long parseOrderIdentifier(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.isBlank()) return null;
        try {
            return Long.valueOf(digits);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public PurchaseOrderResponse getOrder(Long id) {
        return buildDetail(findOrderOrThrow(id));
    }

    @Override
    @Transactional
    public PurchaseOrderResponse receiveOrder(Long id) {
        PurchaseOrderModel order = findOrderOrThrow(id);
        if (order.getStatus() == null || !order.getStatus().isReceivable()) {
            throw new BadRequestException("Only ordered purchase orders can be received.");
        }

        List<PurchaseOrderItemModel> items = purchaseOrderItemRepository.findByPurchaseOrderId(order.getId());
        if (items.isEmpty()) {
            throw new BadRequestException("Purchase order has no items to receive.");
        }

        Map<Integer, ProductModel> productsById = productRepository.findByIdInWithCategory(
                items.stream().map(PurchaseOrderItemModel::getProductId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(ProductModel::getId, p -> p, (a, b) -> a));
        Map<Integer, ProductPackagingModel> topPackagings = loadTopPackagings(productsById);

        Map<Integer, Integer> baseQtyByProduct = new HashMap<>();
        for (PurchaseOrderItemModel item : items) {
            ProductPackagingModel top = topPackagings.get(item.getProductId());
            int baseQty = top != null
                    ? productPackagingService.toBaseQty(safe(item.getQuantity()), top)
                    : productPackagingService.toBaseQty(safe(item.getQuantity()), productsById.get(item.getProductId()));
            if (baseQty > 0 && item.getProductId() != null) {
                baseQtyByProduct.merge(item.getProductId(), baseQty, Integer::sum);
            }
        }
        increaseWarehouseStockBatch(baseQtyByProduct);

        order.setStatus(PurchaseOrderStatus.RECEIVED);
        order.setReceivedAt(LocalDateTime.now());
        PurchaseOrderModel savedOrder = purchaseOrderRepository.save(order);

        // Có hàng rồi -> xét lại các yêu cầu AWAITING_STOCK, đủ tồn thì chuyển APPROVED.
        reevaluateAwaitingStock();

        return buildDetail(savedOrder);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse cancelOrder(Long id) {
        PurchaseOrderModel order = findOrderOrThrow(id);
        if (order.getStatus() == null || !order.getStatus().isCancellable()) {
            throw new BadRequestException("Only ordered purchase orders can be cancelled.");
        }
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        return buildDetail(purchaseOrderRepository.save(order));
    }

    // ----------------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------------

    /**
     * Duyệt lại các yêu cầu AWAITING_STOCK (cũ trước). Dùng bản sao tồn kho tổng để
     * quyết định: nếu đủ cho toàn bộ SL duyệt của yêu cầu -> APPROVED, và trừ vào bản sao
     * để yêu cầu sau không "duyệt ảo" trên cùng lượng hàng. Tồn kho thật chỉ trừ khi gom lô (dispatch).
     */
    private void reevaluateAwaitingStock() {
        List<PurchaseRequestModel> awaiting = purchaseRequestRepository.findByStatus(PurchaseRequestStatus.AWAITING_STOCK);
        if (!awaiting.isEmpty()) {
            awaiting.sort(Comparator.comparing(
                    PurchaseRequestModel::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

            Map<Integer, Integer> workingStock = new HashMap<>(warehouseStockAllocationHelper.workingStockAfterApprovedReservations());
            List<Long> requestIds = awaiting.stream().map(PurchaseRequestModel::getId).toList();
            Map<Long, List<PurchaseRequestDetailModel>> detailsByRequest = detailRepository.findByPurchaseRequestIdIn(requestIds).stream()
                    .collect(Collectors.groupingBy(PurchaseRequestDetailModel::getPurchaseRequestId));

            Set<Integer> productIds = detailsByRequest.values().stream()
                    .flatMap(List::stream)
                    .map(PurchaseRequestDetailModel::getProductId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());
            Map<Integer, ProductModel> productsById = productIds.isEmpty()
                    ? Map.of()
                    : productRepository.findByIdInWithCategory(productIds).stream()
                            .collect(Collectors.toMap(ProductModel::getId, p -> p, (a, b) -> a));
            Map<Integer, ProductPackagingModel> topPackagings = loadTopPackagings(productsById);

            List<PurchaseRequestModel> promoted = new ArrayList<>();
            for (PurchaseRequestModel pr : awaiting) {
                List<PurchaseRequestDetailModel> details = detailsByRequest.getOrDefault(pr.getId(), List.of());
                Map<Integer, Integer> needByProduct = new HashMap<>();
                for (PurchaseRequestDetailModel detail : details) {
                    int needBase = needBaseUnits(detail, productsById, topPackagings);
                    if (needBase > 0 && detail.getProductId() != null) {
                        needByProduct.merge(detail.getProductId(), needBase, Integer::sum);
                    }
                }

                boolean enough = needByProduct.entrySet().stream()
                        .allMatch(e -> workingStock.getOrDefault(e.getKey(), 0) >= e.getValue());
                if (!enough) {
                    continue;
                }
                needByProduct.forEach((productId, qty) ->
                        workingStock.merge(productId, -qty, Integer::sum));
                pr.setStatus(PurchaseRequestStatus.APPROVED);
                promoted.add(pr);
            }
            if (!promoted.isEmpty()) {
                purchaseRequestRepository.saveAll(promoted);
            }
        }
        warehouseStockAllocationHelper.reconcileApprovedStockStatus();
    }

    private Map<Integer, Integer> demandForWarehouseShortfall() {
        return new HashMap<>(demandFromRequests(
                purchaseRequestRepository.findByStatus(PurchaseRequestStatus.AWAITING_STOCK)));
    }

    private Map<Integer, Integer> demandFromRequests(List<PurchaseRequestModel> requests) {
        if (requests.isEmpty()) {
            return Map.of();
        }
        List<Long> requestIds = requests.stream().map(PurchaseRequestModel::getId).toList();
        List<PurchaseRequestDetailModel> allDetails = detailRepository.findByPurchaseRequestIdIn(requestIds);
        Set<Integer> productIds = allDetails.stream()
                .map(PurchaseRequestDetailModel::getProductId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Integer, ProductModel> productsById = productIds.isEmpty()
                ? Map.of()
                : productRepository.findByIdInWithCategory(productIds).stream()
                        .collect(Collectors.toMap(ProductModel::getId, p -> p, (a, b) -> a));
        Map<Integer, ProductPackagingModel> topPackagings = loadTopPackagings(productsById);

        Map<Integer, Integer> demand = new HashMap<>();
        for (PurchaseRequestDetailModel detail : allDetails) {
            int needBase = needBaseUnits(detail, productsById, topPackagings);
            if (needBase > 0 && detail.getProductId() != null) {
                demand.merge(detail.getProductId(), needBase, Integer::sum);
            }
        }
        return demand;
    }

    private int toTopUnits(int baseQty, int conversionQty) {
        if (baseQty <= 0) {
            return 0;
        }
        int conversion = Math.max(conversionQty, 1);
        return (baseQty + conversion - 1) / conversion;
    }

    private int needBaseUnits(
            PurchaseRequestDetailModel detail,
            Map<Integer, ProductModel> productsById,
            Map<Integer, ProductPackagingModel> topPackagings
    ) {
        int topUnits = approvedQuantity(detail);
        if (topUnits <= 0 || detail.getProductId() == null) {
            return 0;
        }
        ProductModel product = productsById.get(detail.getProductId());
        // Short-date products bypass the central warehouse, matching dispatch allocation.
        if (product != null
                && product.getCategory() != null
                && Boolean.TRUE.equals(product.getCategory().getShortDate())) {
            return 0;
        }
        ProductPackagingModel top = topPackagings.get(detail.getProductId());
        if (top != null) {
            return productPackagingService.toBaseQty(topUnits, top);
        }
        return productPackagingService.toBaseQty(topUnits, product);
    }

    private void increaseWarehouseStockBatch(Map<Integer, Integer> baseQtyByProduct) {
        if (baseQtyByProduct == null || baseQtyByProduct.isEmpty()) {
            return;
        }
        Map<Integer, WarehouseInventoryModel> existing = warehouseInventoryRepository
                .findByProductIdIn(baseQtyByProduct.keySet()).stream()
                .collect(Collectors.toMap(WarehouseInventoryModel::getProductId, inv -> inv, (a, b) -> a));
        List<WarehouseInventoryModel> toSave = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : baseQtyByProduct.entrySet()) {
            Integer productId = entry.getKey();
            int quantity = safe(entry.getValue());
            if (productId == null || quantity <= 0) {
                continue;
            }
            WarehouseInventoryModel inventory = existing.get(productId);
            if (inventory == null) {
                inventory = new WarehouseInventoryModel();
                inventory.setProductId(productId);
                inventory.setQuantity(0);
                inventory.setReorderPoint(0);
            }
            inventory.setQuantity(safe(inventory.getQuantity()) + quantity);
            toSave.add(inventory);
        }
        if (!toSave.isEmpty()) {
            warehouseInventoryRepository.saveAll(toSave);
        }
    }

    private Map<Integer, ProductPackagingModel> loadTopPackagings(Map<Integer, ProductModel> productsById) {
        if (productsById == null || productsById.isEmpty()) {
            return Map.of();
        }
        Map<Integer, ProductPackagingModel> topPackagings = new HashMap<>(
                productPackagingService.getTopPackagingsByProductIds(productsById.keySet()));
        for (ProductModel product : productsById.values()) {
            if (!topPackagings.containsKey(product.getId())) {
                ProductPackagingModel top = productPackagingService.getTopPackaging(product);
                if (top != null) {
                    topPackagings.put(product.getId(), top);
                }
            }
        }
        return topPackagings;
    }

    private List<PurchaseOrderResponse> buildSummaries(List<PurchaseOrderModel> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(PurchaseOrderModel::getId).filter(Objects::nonNull).toList();
        Map<Long, List<PurchaseOrderItemModel>> itemsByOrder = orderIds.isEmpty()
                ? Map.of()
                : purchaseOrderItemRepository.findByPurchaseOrderIdIn(orderIds).stream()
                        .collect(Collectors.groupingBy(PurchaseOrderItemModel::getPurchaseOrderId));
        Set<Integer> supplierIds = orders.stream()
                .map(PurchaseOrderModel::getSupplierId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, String> supplierNames = supplierIds.isEmpty()
                ? Map.of()
                : supplierRepository.findAllById(supplierIds).stream()
                        .collect(Collectors.toMap(SupplierModel::getId, SupplierModel::getName, (a, b) -> a));

        List<PurchaseOrderResponse> responses = new ArrayList<>(orders.size());
        for (PurchaseOrderModel order : orders) {
            PurchaseOrderResponse response = new PurchaseOrderResponse();
            response.setId(order.getId());
            response.setOrderNumber(purchaseOrderMapper.toOrderNumber(order));
            response.setSupplierId(order.getSupplierId());
            response.setSupplierName(order.getSupplierId() == null ? null : supplierNames.get(order.getSupplierId()));
            response.setStatus(order.getStatus() == null ? null : order.getStatus().name());
            response.setNotes(order.getNotes());
            response.setCreatedAt(order.getCreatedAt());
            response.setReceivedAt(order.getReceivedAt());
            copyReceivingPartyFields(order, response);
            List<PurchaseOrderItemModel> items = itemsByOrder.getOrDefault(order.getId(), List.of());
            response.setItemCount(items.size());
            int totalQuantity = 0;
            for (PurchaseOrderItemModel item : items) {
                totalQuantity += safe(item.getQuantity());
            }
            response.setTotalQuantity(totalQuantity);
            responses.add(response);
        }
        return responses;
    }

    private PurchaseOrderResponse buildDetail(PurchaseOrderModel order) {
        PurchaseOrderResponse response = new PurchaseOrderResponse();
        response.setId(order.getId());
        response.setOrderNumber(purchaseOrderMapper.toOrderNumber(order));
        response.setSupplierId(order.getSupplierId());
        response.setStatus(order.getStatus() == null ? null : order.getStatus().name());
        response.setNotes(order.getNotes());
        response.setCreatedAt(order.getCreatedAt());
        response.setReceivedAt(order.getReceivedAt());
        copyReceivingPartyFields(order, response);

        if (order.getSupplierId() != null) {
            supplierRepository.findById(order.getSupplierId())
                    .ifPresent(supplier -> response.setSupplierName(supplier.getName()));
        }

        List<PurchaseOrderItemModel> items = purchaseOrderItemRepository.findByPurchaseOrderId(order.getId());
        response.setItemCount(items.size());

        Set<Integer> productIds = items.stream()
                .map(PurchaseOrderItemModel::getProductId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Integer, ProductModel> productsById = productIds.isEmpty()
                ? Map.of()
                : productRepository.findByIdInWithCategory(productIds).stream()
                        .collect(Collectors.toMap(ProductModel::getId, p -> p, (a, b) -> a));

        int totalQuantity = 0;
        for (PurchaseOrderItemModel item : items) {
            ProductModel product = productsById.get(item.getProductId());
            PurchaseOrderResponse.ItemLine line = new PurchaseOrderResponse.ItemLine();
            line.setProductId(item.getProductId());
            line.setProductCode(product == null ? null : product.getCode());
            line.setProductName(product == null ? null : product.getName());
            line.setUnit(product == null ? null : product.getUnit());
            line.setImportUnit(product == null ? null : product.getImportUnit());
            int conversion = product == null || product.getUnitsPerImportUnit() == null
                    ? 1 : Math.max(1, product.getUnitsPerImportUnit());
            line.setConversionQty(conversion);
            line.setQuantity(safe(item.getQuantity()));
            line.setQuantityBase(safe(item.getQuantity()) * conversion);
            line.setUnitPrice(item.getUnitPrice());
            response.getItems().add(line);
            totalQuantity += safe(item.getQuantity());
        }
        response.setTotalQuantity(totalQuantity);
        return response;
    }

    private void copyReceivingPartyFields(PurchaseOrderModel order, PurchaseOrderResponse response) {
        response.setSupplierDeliveryDate(order.getSupplierDeliveryDate());
        response.setDeliveredByName(order.getDeliveredByName());
        response.setDeliveredByPhone(order.getDeliveredByPhone());
        response.setSupplierDocumentNumber(order.getSupplierDocumentNumber());
        response.setReceivedBy(order.getReceivedBy());
        response.setReceivedByName(order.getReceivedByName());
        response.setReceivedByPhone(order.getReceivedByPhone());
    }

    private PurchaseOrderModel findOrderOrThrow(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Purchase order not found."));
    }

    private int approvedQuantity(PurchaseRequestDetailModel detail) {
        if (detail.getApprovedQuantity() != null) {
            return safe(detail.getApprovedQuantity());
        }
        return safe(detail.getRequestedQty());
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
