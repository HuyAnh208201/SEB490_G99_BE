package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Thêm cột `points` vào bảng `users` nếu chưa có.
 * DB dùng ddl-auto=none nên cột mới không tự sinh.
 * Chạy an toàn nhiều lần (idempotent).
 */
@Component
@ConditionalOnStartupBootstrap
@Order(1)
public class UserPointsColumnMigration {

    private static final Logger log = LoggerFactory.getLogger(UserPointsColumnMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        if (isPointsColumnAlreadyExists()) {
            log.info("Column `points` in `users` already exists — skipping migration.");
            return;
        }

        addPointsColumnToUsersTable();
        log.info("Added column `points BIGINT DEFAULT 0` to `users` table.");
    }

    // --- private helpers ---

    private boolean isPointsColumnAlreadyExists() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME   = 'users'
                  AND COLUMN_NAME  = 'points'
                """,
                Integer.class
        );
        return count != null && count > 0;
    }

    private void addPointsColumnToUsersTable() {
        jdbcTemplate.execute(
                "ALTER TABLE users ADD COLUMN points BIGINT NOT NULL DEFAULT 0"
        );
    }
}
