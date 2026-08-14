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
 * Tạo bảng lô vận chuyển (dispatch_orders / dispatch_order_requests) lúc khởi động,
 * bổ sung cột area/route cho branches và seed dữ liệu khu vực/tuyến giao mẫu.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(0)
public class DispatchTableMigration {

    private static final Logger log = LoggerFactory.getLogger(DispatchTableMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS dispatch_orders (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        status VARCHAR(30) NOT NULL DEFAULT 'PREPARING',
                        delivery_area VARCHAR(100) NULL,
                        route VARCHAR(100) NULL,
                        created_by BIGINT NULL,
                        recipient_id BIGINT NULL,
                        shipped_at DATETIME NULL,
                        created_at DATETIME NULL,
                        updated_at DATETIME NULL,
                        delivered_at DATETIME NULL,
                        PRIMARY KEY (id)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS dispatch_order_requests (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        dispatch_order_id BIGINT NOT NULL,
                        purchase_request_id BIGINT NOT NULL,
                        PRIMARY KEY (id),
                        UNIQUE KEY uq_dispatch_request (dispatch_order_id, purchase_request_id)
                    )
                    """);

            addColumnIfMissing("branches", "area", "VARCHAR(100) NULL");
            addColumnIfMissing("branches", "route", "VARCHAR(100) NULL");
            addColumnIfMissing("dispatch_orders", "recipient_id", "BIGINT NULL AFTER created_by");
            addColumnIfMissing("dispatch_orders", "shipped_at", "DATETIME NULL AFTER recipient_id");
            seedBranchAreaRoute();

            log.info("Ensured dispatch_orders / dispatch_order_requests tables and branch area/route columns");
        } catch (Exception ex) {
            log.warn("dispatch migration skipped: {}", ex.getMessage());
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

    private void seedBranchAreaRoute() {
        // Gán khu vực/tuyến giao mẫu theo id chi nhánh nếu chưa có, để chạy được màn gom đơn.
        jdbcTemplate.update("""
                UPDATE branches
                SET area = CASE
                        WHEN (id % 3) = 1 THEN 'Central'
                        WHEN (id % 3) = 2 THEN 'West'
                        ELSE 'East'
                    END,
                    route = CASE
                        WHEN (id % 3) = 1 THEN 'Route A'
                        WHEN (id % 3) = 2 THEN 'Route C'
                        ELSE 'Route B'
                    END
                WHERE area IS NULL OR route IS NULL OR area = '' OR route = ''
                """);
    }
}
