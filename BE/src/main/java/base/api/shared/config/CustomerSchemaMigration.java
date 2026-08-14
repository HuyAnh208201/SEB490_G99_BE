package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.customer-schema-migration-enabled",
        havingValue = "true",
        matchIfMissing = true)
@Order(1)
public class CustomerSchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(CustomerSchemaMigration.class);

    private final JdbcTemplate jdbc;

    public CustomerSchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void migrate() {
        createMembershipTiers();
        addColumn("membership_tiers", "code", "VARCHAR(32) NULL");
        addColumn("membership_tiers", "max_points", "BIGINT NULL");
        addColumn("membership_tiers", "point_multiplier", "DOUBLE NOT NULL DEFAULT 1");
        addColumn("membership_tiers", "benefits_json", "TEXT NULL");
        addColumn("membership_tiers", "sort_order", "INT NOT NULL DEFAULT 0");
        addColumn("membership_tiers", "active", "TINYINT(1) NOT NULL DEFAULT 1");
        addColumn("users", "membership_tier_id", "BIGINT NULL");
        addColumn("users", "date_of_birth", "DATETIME NULL");
        addColumn("users", "gender", "VARCHAR(32) NULL");
        addColumn("users", "is_verified", "TINYINT(1) NOT NULL DEFAULT 1");
        createEmailOtpTokens();
        seedTierIfMissing("SILVER", "Silver", 0, 2499L, 1.0,
                "[\"Priority customer support line\",\"Birthday reward 100 pts\"]", 1);
        seedTierIfMissing("GOLD", "Gold", 2500, 5499L, 1.5,
                "[\"Early access to all promotions\",\"Birthday reward 200 pts\"]", 2);
        seedTierIfMissing("PLATINUM", "Platinum", 5500, null, 2.0,
                "[\"Exclusive member-only deals\",\"Highest point multiplier\"]", 3);
        createUniqueIndexIfSafe();
        log.info("Customer schema migration completed");
    }

    private void createMembershipTiers() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS membership_tiers (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    code VARCHAR(32) NULL,
                    name VARCHAR(64) NOT NULL,
                    min_points BIGINT NOT NULL DEFAULT 0,
                    max_points BIGINT NULL,
                    point_multiplier DOUBLE NOT NULL DEFAULT 1,
                    benefits_json TEXT NULL,
                    sort_order INT NOT NULL DEFAULT 0,
                    active TINYINT(1) NOT NULL DEFAULT 1
                )
                """);
    }

    private void createEmailOtpTokens() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS email_otp_tokens (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    email VARCHAR(255) NOT NULL,
                    purpose VARCHAR(32) NOT NULL,
                    code_hash VARCHAR(128) NOT NULL,
                    payload TEXT NULL,
                    expires_at DATETIME NOT NULL,
                    attempts INT NOT NULL DEFAULT 0,
                    consumed_at DATETIME NULL,
                    created_at DATETIME NOT NULL,
                    INDEX idx_email_otp_lookup (email, purpose, consumed_at, created_at)
                )
                """);
    }

    private void addColumn(String table, String column, String definition) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """, Integer.class, table, column);
        if (count != null && count == 0) {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private void seedTierIfMissing(
            String code,
            String name,
            long min,
            Long max,
            double multiplier,
            String benefits,
            int order) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM membership_tiers WHERE code = ?", Integer.class, code);
        if (count != null && count == 0) {
            jdbc.update("""
                    INSERT INTO membership_tiers
                      (code, name, min_points, max_points, point_multiplier, benefits_json, sort_order, active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 1)
                    """, code, name, min, max, multiplier, benefits, order);
        }
    }

    private void createUniqueIndexIfSafe() {
        try {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'
                      AND INDEX_NAME = 'uq_users_phone'
                    """, Integer.class);
            if (count != null && count == 0) {
                jdbc.execute("CREATE UNIQUE INDEX uq_users_phone ON users (phone)");
            }
        } catch (Exception exception) {
            log.warn("Unique phone index not created; duplicate phone values must be resolved: {}",
                    exception.getMessage());
        }
    }
}
