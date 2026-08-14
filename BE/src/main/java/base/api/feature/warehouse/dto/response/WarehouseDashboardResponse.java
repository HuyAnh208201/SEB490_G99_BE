package base.api.feature.warehouse.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        LocalDate periodFrom,
        LocalDate periodTo,
        long supplierReceipts,
        BigDecimal supplierReceiptValue,
        List<StatusCount> prStatusBreakdown,
        List<StatusCount> dispatchPipeline,
        List<LowStockItem> lowStockItems,
        java.time.LocalDateTime generatedAt
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
