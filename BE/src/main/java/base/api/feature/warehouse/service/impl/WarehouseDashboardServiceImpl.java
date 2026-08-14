package base.api.feature.warehouse.service.impl;

import base.api.feature.dispatch.repository.DispatchOrderRepository;
import base.api.feature.inventory.dto.response.WarehouseInventoryItemResponse;
import base.api.feature.inventory.service.IInventoryService;
import base.api.feature.purchaseorder.repository.PurchaseOrderRepository;
import base.api.feature.purchaseorder.repository.PurchaseOrderItemRepository;
import base.api.shared.entity.PurchaseOrderModel;
import base.api.shared.entity.PurchaseOrderItemModel;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.feature.warehouse.dto.response.WarehouseDashboardResponse;
import base.api.feature.warehouse.dto.response.WarehouseDashboardResponse.LowStockItem;
import base.api.feature.warehouse.dto.response.WarehouseDashboardResponse.StatusCount;
import base.api.feature.warehouse.service.IWarehouseDashboardService;
import base.api.shared.enums.DispatchStatus;
import base.api.shared.enums.PurchaseOrderStatus;
import base.api.shared.enums.PurchaseRequestStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Service
public class WarehouseDashboardServiceImpl implements IWarehouseDashboardService {

    @Autowired
    private WarehouseInventoryRepository warehouseInventoryRepository;

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private DispatchOrderRepository dispatchOrderRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private PurchaseOrderItemRepository purchaseOrderItemRepository;

    @Autowired
    private IInventoryService inventoryService;

    @Override
    public WarehouseDashboardResponse getDashboard() {
        LocalDate today = LocalDate.now();
        return getDashboard(today.withDayOfMonth(1), today);
    }

    @Override
    public WarehouseDashboardResponse getDashboard(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalDate periodFrom = from == null ? today.withDayOfMonth(1) : from;
        LocalDate periodTo = to == null ? today : to;
        if (periodFrom.isAfter(periodTo)) {
            LocalDate swap = periodFrom;
            periodFrom = periodTo;
            periodTo = swap;
        }
        long skuCount = warehouseInventoryRepository.count();
        long totalUnits = warehouseInventoryRepository.sumQuantity();

        List<LowStockItem> lowStockItems = new ArrayList<>();
        for (WarehouseInventoryItemResponse row : inventoryService.getWarehouseLowStock()) {
            lowStockItems.add(new LowStockItem(
                    row.getProductId(),
                    row.getProductName(),
                    row.getProductCode(),
                    row.getQuantity(),
                    row.getReorderPoint()
            ));
        }
        lowStockItems.sort(Comparator.comparing(
                item -> item.quantity() == null ? 0 : item.quantity()));
        long lowStockCount = lowStockItems.size();
        if (lowStockItems.size() > 12) {
            lowStockItems = new ArrayList<>(lowStockItems.subList(0, 12));
        }

        long pending = purchaseRequestRepository.countByStatus(PurchaseRequestStatus.PENDING);
        long awaiting = purchaseRequestRepository.countByStatus(PurchaseRequestStatus.AWAITING_STOCK);
        long approved = purchaseRequestRepository.countByStatus(PurchaseRequestStatus.APPROVED);
        long dispatching = purchaseRequestRepository.countByStatus(PurchaseRequestStatus.DISPATCHING);
        long inTransit = purchaseRequestRepository.countByStatus(PurchaseRequestStatus.IN_TRANSIT);

        long preparing = dispatchOrderRepository.countByStatus(DispatchStatus.PREPARING);
        long delivering = dispatchOrderRepository.countByStatus(DispatchStatus.DELIVERING);
        long redelivery = dispatchOrderRepository.countByStatus(DispatchStatus.REDELIVERY);
        long openPos = purchaseOrderRepository.countByStatus(PurchaseOrderStatus.ORDERED);
        List<PurchaseOrderModel> receipts = purchaseOrderRepository
                .findByStatusAndReceivedAtBetweenOrderByReceivedAtDesc(
                        PurchaseOrderStatus.RECEIVED,
                        periodFrom.atStartOfDay(),
                        periodTo.plusDays(1).atStartOfDay());
        List<Long> receiptIds = receipts.stream().map(PurchaseOrderModel::getId).toList();
        BigDecimal receiptValue = receiptIds.isEmpty() ? BigDecimal.ZERO
                : purchaseOrderItemRepository.findByPurchaseOrderIdIn(receiptIds).stream()
                        .map(item -> lineValue(item))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<StatusCount> prBreakdown = List.of(
                new StatusCount(PurchaseRequestStatus.PENDING.name(), pending),
                new StatusCount(PurchaseRequestStatus.AWAITING_STOCK.name(), awaiting),
                new StatusCount(PurchaseRequestStatus.APPROVED.name(), approved),
                new StatusCount(PurchaseRequestStatus.DISPATCHING.name(), dispatching),
                new StatusCount(PurchaseRequestStatus.IN_TRANSIT.name(), inTransit)
        );

        List<StatusCount> dispatchPipeline = List.of(
                new StatusCount(DispatchStatus.PREPARING.name(), preparing),
                new StatusCount(DispatchStatus.DELIVERING.name(), delivering),
                new StatusCount(DispatchStatus.REDELIVERY.name(), redelivery)
        );

        return new WarehouseDashboardResponse(
                skuCount,
                totalUnits,
                lowStockCount,
                pending,
                awaiting,
                preparing,
                delivering,
                redelivery,
                openPos,
                periodFrom,
                periodTo,
                receipts.size(),
                receiptValue,
                prBreakdown,
                dispatchPipeline,
                lowStockItems,
                LocalDateTime.now()
        );
    }

    private BigDecimal lineValue(PurchaseOrderItemModel item) {
        if (item.getUnitPrice() == null || item.getQuantity() == null) return BigDecimal.ZERO;
        return item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }
}
