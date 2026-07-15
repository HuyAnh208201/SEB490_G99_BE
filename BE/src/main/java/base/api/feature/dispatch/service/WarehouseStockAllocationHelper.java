package base.api.feature.dispatch.service;

import base.api.feature.purchaserequest.repository.PurchaseRequestDetailRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.shared.entity.PurchaseRequestDetailModel;
import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.entity.WarehouseInventoryModel;
import base.api.shared.enums.PurchaseRequestStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tính tồn kho khả dụng sau khi trừ nhu cầu các yêu cầu đã APPROVED (chưa gom lô).
 * Tránh duyệt / gom đơn ảo khi kho tổng không đủ cho nhiều yêu cầu cùng lúc.
 */
@Component
public class WarehouseStockAllocationHelper {

    @Autowired
    private WarehouseInventoryRepository warehouseInventoryRepository;

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private PurchaseRequestDetailRepository detailRepository;

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
        return canFulfillDetails(details, working);
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
        for (PurchaseRequestModel pr : approved) {
            reserveDetails(detailsByRequest.getOrDefault(pr.getId(), List.of()), working);
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

        List<PurchaseRequestModel> result = new ArrayList<>();
        for (PurchaseRequestModel pr : sorted) {
            List<PurchaseRequestDetailModel> details = detailsByRequest.getOrDefault(pr.getId(), List.of());
            if (canFulfillDetails(details, working)) {
                reserveDetails(details, working);
                result.add(pr);
            }
        }
        return result;
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
        for (Long id : ids) {
            reserveDetails(detailsByRequest.getOrDefault(id, List.of()), working);
        }
        return working;
    }

    private boolean canFulfillDetails(List<PurchaseRequestDetailModel> details, Map<Integer, Integer> working) {
        for (PurchaseRequestDetailModel detail : details) {
            int need = approvedQty(detail);
            if (need <= 0 || detail.getProductId() == null) {
                continue;
            }
            if (working.getOrDefault(detail.getProductId(), 0) < need) {
                return false;
            }
        }
        return true;
    }

    private void reserveDetails(List<PurchaseRequestDetailModel> details, Map<Integer, Integer> working) {
        for (PurchaseRequestDetailModel detail : details) {
            int need = approvedQty(detail);
            if (need > 0 && detail.getProductId() != null) {
                working.merge(detail.getProductId(), -need, Integer::sum);
            }
        }
    }

    private int approvedQty(PurchaseRequestDetailModel detail) {
        if (detail.getApprovedQuantity() != null) {
            return safe(detail.getApprovedQuantity());
        }
        return safe(detail.getRequestedQty());
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
