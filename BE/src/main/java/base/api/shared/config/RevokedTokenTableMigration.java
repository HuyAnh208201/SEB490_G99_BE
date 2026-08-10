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
 * Bảng revoked_tokens là blacklist JWT sau khi logout. Khác các *TableMigration
 * khác, migration này KHÔNG gate sau app.startup.bootstrap-enabled: bảng chưa
 * từng tồn tại trên DB dùng chung, thiếu nó thì logout hỏng. CREATE TABLE IF NOT
 * EXISTS là idempotent nên chạy mỗi lần khởi động vẫn rẻ.
 *
 * Lưu token_hash (SHA-256 hex, 64 ký tự) thay vì token thô: JWT nguyên văn là
 * credential, và VARCHAR(1024) utf8mb4 = 4096 byte vượt trần index 3072 byte của
 * InnoDB nên không đánh UNIQUE được.
 */
@Component
@Order(0)
public class RevokedTokenTableMigration {

    private static final Logger log = LoggerFactory.getLogger(RevokedTokenTableMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS revoked_tokens (
                        token_hash VARCHAR(64) NOT NULL,
                        expires_at DATETIME NOT NULL,
                        revoked_at DATETIME NOT NULL,
                        PRIMARY KEY (token_hash),
                        KEY idx_revoked_tokens_expires_at (expires_at)
                    )
                    """);
            log.info("Ensured revoked_tokens table exists");
        } catch (Exception ex) {
            log.warn("revoked_tokens table migration skipped: {}", ex.getMessage());
        }
    }
}
