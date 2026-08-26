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
 * Bảng pos_scan_events là hàng đợi mã vạch cho luồng "điện thoại làm máy quét":
 * điện thoại quét xong ghi 1 dòng, máy bán hàng (web) hỏi định kỳ để lấy mã mới
 * rồi tự thêm vào giỏ. Ghép cặp 2 thiết bị bằng cashier_user_id — cùng tài khoản
 * thu ngân thì hiểu là cùng một phiên bán hàng.
 *
 * Cột error_message lưu lỗi quét (hết hàng / không tìm thấy) để máy bán hàng
 * vẫn nhận được sự kiện qua poll, không chỉ điện thoại.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(0)
public class PosScanEventTableMigration {

    private static final Logger log = LoggerFactory.getLogger(PosScanEventTableMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS pos_scan_events (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        cashier_user_id BIGINT NOT NULL,
                        branch_id BIGINT NULL,
                        barcode VARCHAR(255) NOT NULL,
                        product_id INT NULL,
                        product_name VARCHAR(255) NULL,
                        error_message VARCHAR(500) NULL,
                        created_at DATETIME NOT NULL,
                        PRIMARY KEY (id),
                        KEY idx_pos_scan_events_cashier (cashier_user_id, id)
                    )
                    """);
            log.info("Ensured pos_scan_events table exists");
            addColumnIfMissing("pos_scan_events", "error_message", "VARCHAR(500) NULL AFTER product_name");
        } catch (Exception ex) {
            log.warn("pos_scan_events migration skipped: {}", ex.getMessage());
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
                log.info("Added {}.{}", table, column);
            }
        } catch (Exception ex) {
            log.warn("addColumn {}.{} skipped: {}", table, column, ex.getMessage());
        }
    }
}
