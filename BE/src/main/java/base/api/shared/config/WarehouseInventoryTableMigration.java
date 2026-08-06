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
 * Tạo bảng tồn kho KHO TỔNG (warehouse_inventory) lúc khởi động và seed dữ liệu mẫu
 * nếu bảng đang rỗng (an toàn nếu DBA đã tạo / seed sẵn).
 *
 * Seed cố tình để product_id = 7 tồn thấp (20) nhằm minh hoạ case "hết hàng" (AWAITING_STOCK)
 * khi kho tổng duyệt yêu cầu nhập hàng cần nhiều hơn tồn kho tổng.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(0)
public class WarehouseInventoryTableMigration {

    private static final Logger log = LoggerFactory.getLogger(WarehouseInventoryTableMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS warehouse_inventory (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        product_id INT NOT NULL,
                        quantity INT NOT NULL DEFAULT 0,
                        reorder_point INT NOT NULL DEFAULT 0,
                        updated_at DATETIME NULL,
                        PRIMARY KEY (id),
                        UNIQUE KEY uq_warehouse_inventory_product (product_id)
                    )
                    """);

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM warehouse_inventory", Integer.class);
            if (count == null || count == 0) {
                seedInitialStock();
                log.info("Seeded warehouse_inventory sample data");
            }
            log.info("Ensured warehouse_inventory table exists");
        } catch (Exception ex) {
            log.warn("warehouse_inventory migration skipped: {}", ex.getMessage());
        }
    }

    private void seedInitialStock() {
        // Chỉ seed cho các product đang tồn tại trong bảng products.
        // product_id = 7 để tồn thấp (20) để test luồng "hết hàng".
        jdbcTemplate.update("""
                INSERT INTO warehouse_inventory (product_id, quantity, reorder_point, updated_at)
                SELECT p.id,
                       CASE WHEN p.id = 7 THEN 20 ELSE 500 END AS quantity,
                       50 AS reorder_point,
                       NOW() AS updated_at
                FROM products p
                WHERE NOT EXISTS (
                    SELECT 1 FROM warehouse_inventory w WHERE w.product_id = p.id
                )
                """);
    }
}
