package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * DB dùng ddl-auto=none nên @Entity mới không tự tạo bảng.
 * Tạo bảng đơn đặt hàng nhà cung cấp (purchase_orders / purchase_order_items) lúc khởi động.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(0)
public class PurchaseOrderTableMigration {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderTableMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS purchase_orders (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        supplier_id INT NULL,
                        status VARCHAR(30) NOT NULL DEFAULT 'ORDERED',
                        notes TEXT NULL,
                        created_by BIGINT NULL,
                        created_at DATETIME NULL,
                        updated_at DATETIME NULL,
                        received_at DATETIME NULL,
                        PRIMARY KEY (id)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS purchase_order_items (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        purchase_order_id BIGINT NOT NULL,
                        product_id INT NOT NULL,
                        quantity INT NOT NULL,
                        unit_price DECIMAL(15,2) NULL,
                        PRIMARY KEY (id)
                    )
                    """);
            log.info("Ensured purchase_orders / purchase_order_items tables exist");
        } catch (Exception ex) {
            log.warn("purchase_orders migration skipped: {}", ex.getMessage());
        }
    }
}
