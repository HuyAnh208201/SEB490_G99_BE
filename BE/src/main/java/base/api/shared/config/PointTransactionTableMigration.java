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
 *
 * Bảng point_transactions lưu lịch sử tích/đổi điểm cho báo cáo. Không đặt FK tới
 * users/orders theo cùng lối với PosOrderTablesMigration (tham chiếu mềm).
 */
@Component
@ConditionalOnStartupBootstrap
@Order(0)
public class PointTransactionTableMigration {

    private static final Logger log = LoggerFactory.getLogger(PointTransactionTableMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS point_transactions (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        customer_id BIGINT NOT NULL,
                        order_id BIGINT NULL,
                        points BIGINT NOT NULL,
                        type VARCHAR(32) NOT NULL,
                        created_at DATETIME NOT NULL,
                        PRIMARY KEY (id),
                        KEY idx_point_tx_customer (customer_id),
                        KEY idx_point_tx_order (order_id),
                        KEY idx_point_tx_created (created_at)
                    )
                    """);

            // Bảng point_transactions có thể đã tồn tại từ bản thiết kế gốc với FK
            // customer_id -> customers(id). Cả hệ thống coi khách là user role CUSTOMER
            // (điểm trên users.points), customer_id thực chất trỏ users.id — nên FK sang
            // customers khiến MỌI giao dịch tích điểm (checkout có khách, add-points) chết.
            dropForeignKeyIfPresent("point_transactions", "customer_id", "customers");
            dropForeignKeyIfPresent("point_transactions", "order_id", "orders");

            log.info("Ensured point_transactions table exists");
        } catch (Exception ex) {
            log.warn("point_transactions table migration skipped: {}", ex.getMessage());
        }
    }

    /** Tên FK do MySQL tự sinh nên tra theo cột/bảng đích thay vì đoán tên. */
    private void dropForeignKeyIfPresent(String table, String column, String referencedTable) {
        String constraintName = jdbcTemplate.query(
                """
                        SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                          AND COLUMN_NAME = ?
                          AND REFERENCED_TABLE_NAME = ?
                        LIMIT 1
                        """,
                rs -> rs.next() ? rs.getString(1) : null,
                table,
                column,
                referencedTable);
        if (constraintName != null) {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP FOREIGN KEY " + constraintName);
            log.info("Dropped FK {} on {}.{}", constraintName, table, column);
        }
    }
}
