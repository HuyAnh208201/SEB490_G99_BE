package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Thêm 3 cột mới vào bảng `shifts` để hỗ trợ flow đóng ca và đối soát tiền.
 * - closed_by  : ID của staff đóng ca
 * - staff_note : Ghi chú của staff khi đóng ca
 * - review_note: Ghi chú của BM khi phê duyệt / từ chối
 * Chạy an toàn nhiều lần (idempotent).
 */
@Component
@ConditionalOnStartupBootstrap
@Order(2)
public class ShiftClosingColumnsMigration {

    private static final Logger log = LoggerFactory.getLogger(ShiftClosingColumnsMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        addColumnIfNotExists("closed_by",   "BIGINT NULL");
        addColumnIfNotExists("staff_note",  "VARCHAR(500) NULL");
        addColumnIfNotExists("review_note", "VARCHAR(500) NULL");
        log.info("ShiftClosingColumnsMigration: shifts table is up to date.");
    }

    // --- private helpers ---

    private void addColumnIfNotExists(String columnName, String columnDefinition) {
        if (isColumnAlreadyExists(columnName)) {
            log.info("Column `{}` in `shifts` already exists — skipping.", columnName);
            return;
        }

        jdbcTemplate.execute(
                "ALTER TABLE shifts ADD COLUMN " + columnName + " " + columnDefinition
        );
        log.info("Added column `{}` to `shifts` table.", columnName);
    }

    private boolean isColumnAlreadyExists(String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME   = 'shifts'
                  AND COLUMN_NAME  = ?
                """,
                Integer.class,
                columnName
        );
        return count != null && count > 0;
    }
}
