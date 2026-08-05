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
 * Bảng order_refunds lưu yêu cầu hoàn/trả hàng chờ BM duyệt. Không đặt FK tới
 * orders/users theo cùng lối với PosOrderTablesMigration (tham chiếu mềm).
 */
@Component
@ConditionalOnStartupBootstrap
@Order(0)
public class OrderRefundTableMigration {

    private static final Logger log = LoggerFactory.getLogger(OrderRefundTableMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS order_refunds (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        order_id BIGINT NOT NULL,
                        branch_id BIGINT NOT NULL,
                        requested_by BIGINT NOT NULL,
                        reason VARCHAR(500) NULL,
                        status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                        reviewed_by BIGINT NULL,
                        review_note VARCHAR(500) NULL,
                        created_at DATETIME NOT NULL,
                        reviewed_at DATETIME NULL,
                        PRIMARY KEY (id),
                        KEY idx_order_refunds_branch_status (branch_id, status),
                        KEY idx_order_refunds_order (order_id)
                    )
                    """);
            log.info("Ensured order_refunds table exists");
        } catch (Exception ex) {
            log.warn("order_refunds table migration skipped: {}", ex.getMessage());
        }
    }
}
