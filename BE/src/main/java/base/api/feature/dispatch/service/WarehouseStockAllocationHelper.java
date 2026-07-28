package base.api.feature.dispatch.service;

import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.purchaserequest.repository.PurchaseRequestDetailRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.shared.entity.ProductModel;
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

    /**
     * Kiểm tra yêu cầu (sau khi set approved qty) có đủ tồn sau khi trừ các yêu cầu APPROVED khác.
     */
    public boolean canApproveRequest(Long requestId, List<PurchaseRequestDetailModel> details) {
        Map<Integer, Integer> working = workingStockExcludingRequest(requestId);
        Map<Integer, ProductModel> productsById = loadProducts(List.of(details));
        return canFulfillDetails(details, working, productsById);
    }

    /**
     * Tồn kho vật lý sau khi trừ nhu cầu tất cả yêu cầu APPROVED (chưa gom lô).
     */
    public Map<Integer, Integer> workingStockAfterApprovedReservations() {
        Map<Integer, Integer> working = loadPhysicalStock();
        List<PurchaseRequestModel> approved = purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED);
        if (approved.isEmpty()) {
            return working;
        }
        List<Long> ids = approved.stream().map(PurchaseRequestModel::getId).toList();
        Map<Long, List<PurchaseRequestDetailModel>> detailsByRequest =
                detailRepository.findByPurchaseRequestIdIn(ids).stream()
                        .collect(Collectors.groupingBy(PurchaseRequestDetailModel::getPurchaseRequestId));
        Map<Integer, ProductModel> productsById = loadProducts(detailsByRequest.values());
        for (PurchaseRequestModel pr : approved) {
            reserveDetails(detailsByRequest.getOrDefault(pr.getId(), List.of()), working, productsById);
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

        Map<Integer, Integer> working = loadPhysicalStock();
        List<Long> ids = sorted.stream().map(PurchaseRequestModel::getId).toList();
        Map<Long, List<PurchaseRequestDetailModel>> detailsByRequest =
                detailRepository.findByPurchaseRequestIdIn(ids).stream()
                        .collect(Collectors.groupingBy(PurchaseRequestDetailModel::getPurchaseRequestId));
        Map<Integer, ProductModel> productsById = loadProducts(detailsByRequest.values());

        List<PurchaseRequestModel> result = new ArrayList<>();
        for (PurchaseRequestModel pr : sorted) {
            List<PurchaseRequestDetailModel> details = detailsByRequest.getOrDefault(pr.getId(), List.of());
            if (canFulfillDetails(details, working, productsById)) {
                reserveDetails(details, working, productsById);
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

    private Map<Integer, Integer> workingStockExcludingRequest(Long excludeRequestId) {
        Map<Integer, Integer> working = loadPhysicalStock();
        List<PurchaseRequestModel> others = purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED);
        if (others.isEmpty()) {
            return working;
        }
        List<Long> ids = others.stream()
                .filter(pr -> excludeRequestId == null || !excludeRequestId.equals(pr.getId()))
                .map(PurchaseRequestModel::getId)
                .toList();
        if (ids.isEmpty()) {
            return working;
        }
        Map<Long, List<PurchaseRequestDetailModel>> detailsByRequest =
                detailRepository.findByPurchaseRequestIdIn(ids).stream()
                        .collect(Collectors.groupingBy(PurchaseRequestDetailModel::getPurchaseRequestId));
        Map<Integer, ProductModel> productsById = loadProducts(detailsByRequest.values());
        for (Long id : ids) {
            reserveDetails(detailsByRequest.getOrDefault(id, List.of()), working, productsById);
        }
        return working;
    }

    private boolean canFulfillDetails(
            List<PurchaseRequestDetailModel> details,
            Map<Integer, Integer> working,
            Map<Integer, ProductModel> productsById
    ) {
        for (PurchaseRequestDetailModel detail : details) {
            int need = needBaseUnits(detail, productsById);
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
            Map<Integer, ProductModel> productsById
    ) {
        for (PurchaseRequestDetailModel detail : details) {
            int need = needBaseUnits(detail, productsById);
            if (need > 0 && detail.getProductId() != null) {
                working.merge(detail.getProductId(), -need, Integer::sum);
            }
        }
    }

    /** Requested/approved quantity converted from TOP packaging units into BASE stock units. */
    private int needBaseUnits(PurchaseRequestDetailModel detail, Map<Integer, ProductModel> productsById) {
        int topUnits = approvedQty(detail);
        if (topUnits <= 0) {
            return 0;
        }
        ProductModel product = productsById.get(detail.getProductId());
        return productPackagingService.toBaseQty(topUnits, product);
    }

    private int approvedQty(PurchaseRequestDetailModel detail) {
        if (detail.getApprovedQuantity() != null) {
            return safe(detail.getApprovedQuantity());
        }
        return safe(detail.getRequestedQty());
    }

    private Map<Integer, ProductModel> loadProducts(Iterable<List<PurchaseRequestDetailModel>> detailGroups) {
        Set<Integer> productIds = new HashSet<>();
        for (List<PurchaseRequestDetailModel> details : detailGroups) {
            for (PurchaseRequestDetailModel detail : details) {
                if (detail.getProductId() != null) {
                    productIds.add(detail.getProductId());
                }
            }
        }
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findByIdInWithCategory(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, p -> p, (a, b) -> a));
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
