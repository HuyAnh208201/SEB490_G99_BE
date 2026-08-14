package base.api.feature.dispatch.service;

import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.purchaserequest.repository.PurchaseRequestDetailRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.ProductPackagingModel;
import base.api.shared.entity.PurchaseRequestDetailModel;
import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.entity.WarehouseInventoryModel;
import base.api.shared.enums.PurchaseRequestStatus;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tính tồn kho khả dụng sau khi trừ nhu cầu các yêu cầu đã APPROVED (chưa gom lô).
 * Tránh duyệt / gom đơn ảo khi kho tổng không đủ cho nhiều yêu cầu cùng lúc.
 *
 * Warehouse stock is tracked in BASE units, while purchase-request quantities
 * (requested/approved) are entered in TOP packaging units, so every "need" is
 * converted to base units via {@link ProductPackagingService} before being
 * compared against or subtracted from the working stock map.
 */
@Component
public class WarehouseStockAllocationHelper {

    @Autowired
    private WarehouseInventoryRepository warehouseInventoryRepository;

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private PurchaseRequestDetailRepository detailRepository;

    @Autowired
    private IProductRepository productRepository;

    @Autowired
    private ProductPackagingService productPackagingService;

    public Map<Integer, Integer> loadPhysicalStock() {
        Map<Integer, Integer> stock = new HashMap<>();
        for (WarehouseInventoryModel row : warehouseInventoryRepository.findAll()) {
            stock.put(row.getProductId(), safe(row.getQuantity()));
        }
        return stock;
    }

    public Map<Integer, Integer> loadPhysicalStock(Set<Integer> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return loadPhysicalStock();
        }
        Map<Integer, Integer> stock = new HashMap<>();
        for (Integer productId : productIds) {
            stock.put(productId, 0);
        }
        for (WarehouseInventoryModel row : warehouseInventoryRepository.findByProductIdIn(productIds)) {
            stock.put(row.getProductId(), safe(row.getQuantity()));
        }
        return stock;
    }

    /**
     * Kiểm tra yêu cầu (sau khi set approved qty) có đủ tồn sau khi trừ các yêu cầu APPROVED khác.
     */
    public boolean canApproveRequest(Long requestId, List<PurchaseRequestDetailModel> details) {
        Map<Long, List<PurchaseRequestDetailModel>> othersByRequest = loadApprovedDetailsExcluding(requestId);
        Set<Integer> productIds = collectProductIds(othersByRequest.values());
        productIds.addAll(collectProductIds(List.of(details)));

        Map<Integer, Integer> working = loadPhysicalStock(productIds);
        Map<Integer, ProductModel> productsById = loadProductsByIds(productIds);
        Map<Integer, ProductPackagingModel> topPackagings = loadPackagings(productsById);

        for (List<PurchaseRequestDetailModel> otherDetails : othersByRequest.values()) {
            reserveDetails(otherDetails, working, productsById, topPackagings);
        }
        return canFulfillDetails(details, working, productsById, topPackagings);
    }

    /**
     * Tồn kho vật lý sau khi trừ nhu cầu tất cả yêu cầu APPROVED (chưa gom lô).
     */
    public Map<Integer, Integer> workingStockAfterApprovedReservations() {
        List<PurchaseRequestModel> approved = purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED);
        if (approved.isEmpty()) {
            return loadPhysicalStock();
        }
        List<Long> ids = approved.stream().map(PurchaseRequestModel::getId).toList();
        Map<Long, List<PurchaseRequestDetailModel>> detailsByRequest =
                detailRepository.findByPurchaseRequestIdIn(ids).stream()
                        .collect(Collectors.groupingBy(PurchaseRequestDetailModel::getPurchaseRequestId));
        Set<Integer> productIds = collectProductIds(detailsByRequest.values());
        // Awaiting requests may contain products that are not present in any approved
        // request. Keep every physical-stock row in the working map so reevaluation
        // does not incorrectly treat those products as having zero stock.
        Map<Integer, Integer> working = loadPhysicalStock();
        Map<Integer, ProductModel> productsById = loadProductsByIds(productIds);
        Map<Integer, ProductPackagingModel> topPackagings = loadPackagings(productsById);
        for (PurchaseRequestModel pr : approved) {
            reserveDetails(
                    detailsByRequest.getOrDefault(pr.getId(), List.of()),
                    working,
                    productsById,
                    topPackagings);
        }
        return working;
    }

    /**
     * Lọc các yêu cầu APPROVED thực sự đủ hàng để gom lô (FIFO theo createdAt).
     */
    public List<PurchaseRequestModel> filterDispatchableApproved(List<PurchaseRequestModel> approved) {
        if (approved.isEmpty()) {
            return List.of();
        }
        List<PurchaseRequestModel> sorted = approved.stream()
                .sorted(Comparator.comparing(
                        PurchaseRequestModel::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<Long> ids = sorted.stream().map(PurchaseRequestModel::getId).toList();
        Map<Long, List<PurchaseRequestDetailModel>> detailsByRequest =
                detailRepository.findByPurchaseRequestIdIn(ids).stream()
                        .collect(Collectors.groupingBy(PurchaseRequestDetailModel::getPurchaseRequestId));
        Set<Integer> productIds = collectProductIds(detailsByRequest.values());
        Map<Integer, Integer> working = loadPhysicalStock(productIds);
        Map<Integer, ProductModel> productsById = loadProductsByIds(productIds);
        Map<Integer, ProductPackagingModel> topPackagings = loadPackagings(productsById);

        List<PurchaseRequestModel> result = new ArrayList<>();
        for (PurchaseRequestModel pr : sorted) {
            List<PurchaseRequestDetailModel> details = detailsByRequest.getOrDefault(pr.getId(), List.of());
            if (canFulfillDetails(details, working, productsById, topPackagings)) {
                reserveDetails(details, working, productsById, topPackagings);
                result.add(pr);
            }
        }
        return result;
    }

    /**
     * Demote APPROVED requests that cannot actually be dispatched (insufficient warehouse stock
     * after TOP→BASE conversion) to AWAITING_STOCK so Incoming and PO recommendations stay in sync.
     *
     * @return number of requests demoted
     */
    @Transactional
    public int reconcileApprovedStockStatus() {
        List<PurchaseRequestModel> approved = purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED);
        if (approved.isEmpty()) {
            return 0;
        }
        Set<Long> dispatchableIds = filterDispatchableApproved(approved).stream()
                .map(PurchaseRequestModel::getId)
                .collect(Collectors.toSet());

        List<PurchaseRequestModel> demoted = new ArrayList<>();
        for (PurchaseRequestModel pr : approved) {
            if (!dispatchableIds.contains(pr.getId())) {
                pr.setStatus(PurchaseRequestStatus.AWAITING_STOCK);
                demoted.add(pr);
            }
        }
        if (!demoted.isEmpty()) {
            purchaseRequestRepository.saveAll(demoted);
        }
        return demoted.size();
    }

    private Map<Long, List<PurchaseRequestDetailModel>> loadApprovedDetailsExcluding(Long excludeRequestId) {
        List<PurchaseRequestModel> others = purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED);
        List<Long> ids = others.stream()
                .filter(pr -> excludeRequestId == null || !excludeRequestId.equals(pr.getId()))
                .map(PurchaseRequestModel::getId)
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return detailRepository.findByPurchaseRequestIdIn(ids).stream()
                .collect(Collectors.groupingBy(PurchaseRequestDetailModel::getPurchaseRequestId));
    }

    private boolean canFulfillDetails(
            List<PurchaseRequestDetailModel> details,
            Map<Integer, Integer> working,
            Map<Integer, ProductModel> productsById,
            Map<Integer, ProductPackagingModel> topPackagings
    ) {
        for (PurchaseRequestDetailModel detail : details) {
            int need = needBaseUnits(detail, productsById, topPackagings);
            if (need <= 0 || detail.getProductId() == null) {
                continue;
            }
            if (working.getOrDefault(detail.getProductId(), 0) < need) {
                return false;
            }
        }
        return true;
    }

    private void reserveDetails(
            List<PurchaseRequestDetailModel> details,
            Map<Integer, Integer> working,
            Map<Integer, ProductModel> productsById,
            Map<Integer, ProductPackagingModel> topPackagings
    ) {
        for (PurchaseRequestDetailModel detail : details) {
            int need = needBaseUnits(detail, productsById, topPackagings);
            if (need > 0 && detail.getProductId() != null) {
                working.merge(detail.getProductId(), -need, Integer::sum);
            }
        }
    }

    /** Requested/approved quantity converted from TOP packaging units into BASE stock units. */
    private int needBaseUnits(
            PurchaseRequestDetailModel detail,
            Map<Integer, ProductModel> productsById,
            Map<Integer, ProductPackagingModel> topPackagings
    ) {
        int topUnits = approvedQty(detail);
        if (topUnits <= 0 || detail.getProductId() == null) {
            return 0;
        }
        ProductModel product = productsById.get(detail.getProductId());
        // Short-date products are not held in central warehouse — skip stock reservation.
        if (isShortDateProduct(product)) {
            return 0;
        }
        ProductPackagingModel top = topPackagings.get(detail.getProductId());
        if (top != null) {
            return productPackagingService.toBaseQty(topUnits, top);
        }
        return productPackagingService.toBaseQty(topUnits, product);
    }

    private boolean isShortDateProduct(ProductModel product) {
        return product != null
                && product.getCategory() != null
                && Boolean.TRUE.equals(product.getCategory().getShortDate());
    }

    private int approvedQty(PurchaseRequestDetailModel detail) {
        if (detail.getApprovedQuantity() != null) {
            return safe(detail.getApprovedQuantity());
        }
        return safe(detail.getRequestedQty());
    }

    private Set<Integer> collectProductIds(Iterable<List<PurchaseRequestDetailModel>> detailGroups) {
        Set<Integer> productIds = new HashSet<>();
        for (List<PurchaseRequestDetailModel> details : detailGroups) {
            for (PurchaseRequestDetailModel detail : details) {
                if (detail.getProductId() != null) {
                    productIds.add(detail.getProductId());
                }
            }
        }
        return productIds;
    }

    private Map<Integer, ProductModel> loadProductsByIds(Set<Integer> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findByIdInWithCategory(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, p -> p, (a, b) -> a));
    }

    private Map<Integer, ProductPackagingModel> loadPackagings(Map<Integer, ProductModel> productsById) {
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

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
