package base.api.feature.purchaseorder.service.impl;

import base.api.feature.product.repository.IProductRepository;
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
import base.api.shared.entity.PurchaseOrderItemModel;
import base.api.shared.entity.PurchaseOrderModel;
import base.api.shared.entity.PurchaseRequestDetailModel;
import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.entity.SupplierModel;
import base.api.shared.entity.WarehouseInventoryModel;
import base.api.shared.enums.PurchaseOrderStatus;
import base.api.shared.enums.PurchaseRequestStatus;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Override
    public List<RecommendedPurchaseProductResponse> getRecommendedProducts() {
        Map<Integer, WarehouseInventoryModel> stockByProduct = warehouseInventoryRepository.findAll().stream()
                .collect(Collectors.toMap(WarehouseInventoryModel::getProductId, inv -> inv, (a, b) -> a));

        // Nhu cầu từ các yêu cầu đang chờ tồn kho (AWAITING_STOCK).
        Map<Integer, Integer> demandByProduct = demandForAwaitingStock();

        Set<Integer> productIds = new LinkedHashSet<>(demandByProduct.keySet());
        for (WarehouseInventoryModel inv : stockByProduct.values()) {
            if (safe(inv.getQuantity()) < safe(inv.getReorderPoint())) {
                productIds.add(inv.getProductId());
            }
        }
        if (productIds.isEmpty()) {
            return List.of();
        }

        Map<Integer, ProductModel> productsById = productRepository.findByIdInWithCategory(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, p -> p, (a, b) -> a));

        List<RecommendedPurchaseProductResponse> result = new ArrayList<>();
        for (Integer productId : productIds) {
            ProductModel product = productsById.get(productId);
            if (product == null) {
                continue;
            }
            WarehouseInventoryModel inv = stockByProduct.get(productId);
            int current = inv == null ? 0 : safe(inv.getQuantity());
            int reorder = inv == null ? 0 : safe(inv.getReorderPoint());
            int demand = demandByProduct.getOrDefault(productId, 0);
            int required = Math.max(demand, reorder);
            int suggested = Math.max(required - current, 0);
            if (suggested <= 0) {
                continue;
            }

            RecommendedPurchaseProductResponse row = new RecommendedPurchaseProductResponse();
            row.setProductId(productId);
            row.setProductCode(product.getCode());
            row.setProductName(product.getName());
            row.setCategoryName(product.getCategory() == null ? null : product.getCategory().getName());
            row.setUnit(product.getUnit());
            row.setCurrentQty(current);
            row.setRequiredQty(required);
            row.setSuggestedQty(suggested);
            row.setReferencePrice(product.getReferenceImportPrice());
            result.add(row);
        }
        result.sort(Comparator.comparing(
                RecommendedPurchaseProductResponse::getSuggestedQty, Comparator.reverseOrder()));
        return result;
    }

    @Override
    public List<PurchaseProductOptionResponse> searchProducts(String keyword) {
        Map<Integer, Integer> stockByProduct = warehouseStockMap();
        return productRepository
                .searchActiveProducts(normalize(keyword), PageRequest.of(0, SEARCH_LIMIT, Sort.by(Sort.Direction.ASC, "name")))
                .getContent().stream()
                .map(product -> {
                    PurchaseProductOptionResponse row = new PurchaseProductOptionResponse();
                    row.setProductId(product.getId());
                    row.setProductCode(product.getCode());
                    row.setProductName(product.getName());
                    row.setCategoryName(product.getCategory() == null ? null : product.getCategory().getName());
                    row.setUnit(product.getUnit());
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

        PurchaseOrderModel order = new PurchaseOrderModel();
        order.setSupplierId(supplier.getId());
        order.setStatus(PurchaseOrderStatus.ORDERED);
        order.setNotes(normalize(request.getNotes()));
        order.setCreatedBy(currentUserProvider.getCurrentUserOrThrow().getId());
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
        }
        purchaseOrderItemRepository.saveAll(items);

        return buildDetail(savedOrder);
    }

    @Override
    public List<PurchaseOrderResponse> getOrders() {
        return purchaseOrderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::buildDetail)
                .toList();
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

        // Nhập kho tổng: cộng tồn kho KHO TỔNG cho từng sản phẩm.
        for (PurchaseOrderItemModel item : items) {
            increaseWarehouseStock(item.getProductId(), safe(item.getQuantity()));
        }

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
        if (awaiting.isEmpty()) {
            return;
        }
        awaiting.sort(Comparator.comparing(
                PurchaseRequestModel::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

        Map<Integer, Integer> workingStock = new HashMap<>(warehouseStockMap());
        List<Long> requestIds = awaiting.stream().map(PurchaseRequestModel::getId).toList();
        Map<Long, List<PurchaseRequestDetailModel>> detailsByRequest = detailRepository.findByPurchaseRequestIdIn(requestIds).stream()
                .collect(Collectors.groupingBy(PurchaseRequestDetailModel::getPurchaseRequestId));

        List<PurchaseRequestModel> promoted = new ArrayList<>();
        for (PurchaseRequestModel pr : awaiting) {
            List<PurchaseRequestDetailModel> details = detailsByRequest.getOrDefault(pr.getId(), List.of());
            Map<Integer, Integer> needByProduct = new HashMap<>();
            for (PurchaseRequestDetailModel detail : details) {
                int qty = approvedQuantity(detail);
                if (qty > 0 && detail.getProductId() != null) {
                    needByProduct.merge(detail.getProductId(), qty, Integer::sum);
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

    private Map<Integer, Integer> demandForAwaitingStock() {
        List<PurchaseRequestModel> awaiting = purchaseRequestRepository.findByStatus(PurchaseRequestStatus.AWAITING_STOCK);
        if (awaiting.isEmpty()) {
            return Map.of();
        }
        List<Long> requestIds = awaiting.stream().map(PurchaseRequestModel::getId).toList();
        Map<Integer, Integer> demand = new HashMap<>();
        for (PurchaseRequestDetailModel detail : detailRepository.findByPurchaseRequestIdIn(requestIds)) {
            int qty = approvedQuantity(detail);
            if (qty > 0 && detail.getProductId() != null) {
                demand.merge(detail.getProductId(), qty, Integer::sum);
            }
        }
        return demand;
    }

    private Map<Integer, Integer> warehouseStockMap() {
        return warehouseInventoryRepository.findAll().stream()
                .collect(Collectors.toMap(
                        WarehouseInventoryModel::getProductId,
                        inv -> safe(inv.getQuantity()),
                        (a, b) -> a));
    }

    private void increaseWarehouseStock(Integer productId, int quantity) {
        if (productId == null || quantity <= 0) {
            return;
        }
        WarehouseInventoryModel inventory = warehouseInventoryRepository.findByProductId(productId)
                .orElseGet(() -> {
                    WarehouseInventoryModel created = new WarehouseInventoryModel();
                    created.setProductId(productId);
                    created.setQuantity(0);
                    created.setReorderPoint(0);
                    return created;
                });
        inventory.setQuantity(safe(inventory.getQuantity()) + quantity);
        warehouseInventoryRepository.save(inventory);
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
            line.setQuantity(safe(item.getQuantity()));
            line.setUnitPrice(item.getUnitPrice());
            response.getItems().add(line);
            totalQuantity += safe(item.getQuantity());
        }
        response.setTotalQuantity(totalQuantity);
        return response;
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
