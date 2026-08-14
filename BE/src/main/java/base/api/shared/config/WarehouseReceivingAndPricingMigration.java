package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Idempotent schema bootstrap for Module G supplier receipts and dated retail prices. */
@Component
@ConditionalOnStartupBootstrap
@Order(0)
public class WarehouseReceivingAndPricingMigration {
    private static final Logger log = LoggerFactory.getLogger(WarehouseReceivingAndPricingMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        addColumnIfMissing("products", "supplier_id", "INT NULL AFTER branch_id");
        addColumnIfMissing("purchase_orders", "supplier_delivery_date", "DATE NULL AFTER created_by");
        addColumnIfMissing("purchase_orders", "delivered_by_name", "VARCHAR(255) NULL AFTER supplier_delivery_date");
        addColumnIfMissing("purchase_orders", "delivered_by_phone", "VARCHAR(32) NULL AFTER delivered_by_name");
        addColumnIfMissing("purchase_orders", "supplier_document_number", "VARCHAR(100) NULL AFTER delivered_by_phone");
        addColumnIfMissing("purchase_orders", "received_by", "BIGINT NULL AFTER supplier_document_number");
        addColumnIfMissing("purchase_orders", "received_by_name", "VARCHAR(255) NULL AFTER received_by");
        addColumnIfMissing("purchase_orders", "received_by_phone", "VARCHAR(32) NULL AFTER received_by_name");
        createPriceScheduleTableIfMissing();
    }

    private void createPriceScheduleTableIfMissing() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS product_sale_prices (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        product_id INT NOT NULL,
                        price DECIMAL(15,2) NOT NULL,
                        effective_date DATE NOT NULL,
                        created_by BIGINT NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT uq_product_sale_price_date UNIQUE (product_id, effective_date),
                        INDEX idx_product_sale_price_effective (product_id, effective_date)
                    )
                    """);
        } catch (Exception ex) {
            log.warn("Module G price schedule table skipped: {}", ex.getMessage());
        }
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                    """, Integer.class, table, column);
            if (count == null || count == 0) {
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                log.info("Added Module G column {}.{}", table, column);
            }
        } catch (Exception ex) {
            log.warn("Module G column {}.{} skipped: {}", table, column, ex.getMessage());
        }
    }
}
