package base.api.feature.warehouse.dto.response;

import java.util.List;

/** Central warehouse ops home — stock risk + fulfillment queues. */
public record WarehouseDashboardResponse(
        long skuCount,
        long totalUnits,
        long lowStockCount,
        long pendingRequests,
        long awaitingStockRequests,
        long preparingDispatches,
        long deliveringDispatches,
        long redeliveryDispatches,
        long openPurchaseOrders,
        List<StatusCount> prStatusBreakdown,
        List<StatusCount> dispatchPipeline,
        List<LowStockItem> lowStockItems
) {
    public record StatusCount(String status, long count) {
    }

    public record LowStockItem(
            Integer productId,
            String productName,
            String productCode,
            Integer quantity,
            Integer reorderPoint
    ) {
    }
}
