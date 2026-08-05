package base.api.shared.config;

import base.api.feature.dispatch.service.WarehouseStockAllocationHelper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One-time startup reconciliation: demote APPROVED purchase requests that are not
 * actually dispatchable (warehouse stock shortfall) to AWAITING_STOCK.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(1)
public class PurchaseRequestStockReconciliationMigration {

    private static final Logger log = LoggerFactory.getLogger(PurchaseRequestStockReconciliationMigration.class);

    @Autowired
    private WarehouseStockAllocationHelper warehouseStockAllocationHelper;

    @PostConstruct
    public void migrate() {
        try {
            int demoted = warehouseStockAllocationHelper.reconcileApprovedStockStatus();
            if (demoted > 0) {
                log.info("Reconciled purchase request stock status: {} APPROVED -> AWAITING_STOCK", demoted);
            }
        } catch (Exception ex) {
            log.warn("Purchase request stock reconciliation skipped: {}", ex.getMessage());
        }
    }
}
