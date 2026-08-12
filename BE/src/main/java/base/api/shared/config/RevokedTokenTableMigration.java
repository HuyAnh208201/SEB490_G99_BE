package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The database runs with ddl-auto=none, so a new @Entity creates no table by itself.
 *
 * revoked_tokens holds JWTs invalidated by logout. Unlike the other *TableMigration
 * classes this one is NOT gated behind app.startup.bootstrap-enabled: the table has
 * never existed on the shared database and logout breaks without it. CREATE TABLE IF
 * NOT EXISTS is idempotent, so running it on every startup stays cheap.
 *
 * Stores token_hash (SHA-256 hex, 64 chars) rather than the raw token: a JWT is a
 * credential, and VARCHAR(1024) utf8mb4 is 4096 bytes, over the 3072-byte index limit
 * InnoDB allows, so it could not be made UNIQUE.
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
