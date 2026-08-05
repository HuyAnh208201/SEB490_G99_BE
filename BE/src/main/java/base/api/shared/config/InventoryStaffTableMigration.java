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
 * Tạo bảng kiểm kê (inventory_count_sessions / inventory_count_items) lúc khởi động
 * và bổ sung cột phục vụ nhập kho thực tế cho goods_receipts / goods_receipt_items.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(0)
public class InventoryStaffTableMigration {

    private static final Logger log = LoggerFactory.getLogger(InventoryStaffTableMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS inventory_count_sessions (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        branch_id BIGINT NOT NULL,
                        count_date DATE NOT NULL,
                        counted_by BIGINT NOT NULL,
                        status VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL',
                        total_products INT NULL DEFAULT 0,
                        note TEXT NULL,
                        reviewed_by BIGINT NULL,
                        reviewed_at DATETIME NULL,
                        created_at DATETIME NULL,
                        updated_at DATETIME NULL,
                        PRIMARY KEY (id)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS inventory_count_items (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        session_id BIGINT NOT NULL,
                        product_id INT NOT NULL,
                        system_qty INT NOT NULL DEFAULT 0,
                        counted_qty INT NOT NULL DEFAULT 0,
                        variance INT NOT NULL DEFAULT 0,
                        note VARCHAR(500) NULL,
                        PRIMARY KEY (id),
                        KEY idx_count_item_session (session_id)
                    )
                    """);

            addColumnIfMissing("goods_receipts", "dispatch_order_id", "BIGINT NULL");
            addColumnIfMissing("goods_receipt_items", "note", "VARCHAR(500) NULL");

            log.info("Ensured inventory_count tables and goods_receipts receiving columns");
        } catch (Exception ex) {
            log.warn("inventory staff migration skipped: {}", ex.getMessage());
        }
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                    """,
                    Integer.class, table, column);
            if (exists == null || exists == 0) {
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        } catch (Exception ex) {
            log.warn("addColumn {}.{} skipped: {}", table, column, ex.getMessage());
        }
    }
}
