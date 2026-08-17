package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Keeps short-lived user token tables removable when a user is hard-deleted.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(2)
public class UserTokenCascadeMigration {

    private static final Logger log = LoggerFactory.getLogger(UserTokenCascadeMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        ensureTokenCascade("password_reset_tokens", "user_id", "fk_password_reset_tokens_user");
        ensureTokenCascade("email_verification_tokens", "user_id", "fk_email_verification_tokens_user");
    }

    private void ensureTokenCascade(String table, String column, String constraintName) {
        try {
            if (!tableExists(table) || !columnExists(table, column) || !tableExists("users")) {
                return;
            }

            List<Map<String, Object>> constraints = userForeignKeys(table, column);
            boolean hasCascade = constraints.stream()
                    .anyMatch(row -> "CASCADE".equalsIgnoreCase(String.valueOf(row.get("DELETE_RULE"))));
            if (hasCascade) {
                return;
            }

            for (Map<String, Object> row : constraints) {
                String existingName = String.valueOf(row.get("CONSTRAINT_NAME"));
                jdbcTemplate.execute("ALTER TABLE `" + table + "` DROP FOREIGN KEY `" + existingName + "`");
            }

            jdbcTemplate.execute("""
                    ALTER TABLE `%s`
                    ADD CONSTRAINT `%s`
                    FOREIGN KEY (`%s`) REFERENCES `users` (`id`) ON DELETE CASCADE
                    """.formatted(table, constraintName, column));
            log.info("Ensured ON DELETE CASCADE for {}.{}", table, column);
        } catch (Exception ex) {
            log.warn("User token cascade migration skipped for {}.{}: {}", table, column, ex.getMessage());
        }
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.TABLES
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                        """,
                Integer.class,
                table);
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                          AND COLUMN_NAME = ?
                        """,
                Integer.class,
                table,
                column);
        return count != null && count > 0;
    }

    private List<Map<String, Object>> userForeignKeys(String table, String column) {
        return jdbcTemplate.queryForList(
                """
                        SELECT k.CONSTRAINT_NAME, r.DELETE_RULE
                        FROM information_schema.KEY_COLUMN_USAGE k
                        JOIN information_schema.REFERENTIAL_CONSTRAINTS r
                          ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
                         AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
                        WHERE k.TABLE_SCHEMA = DATABASE()
                          AND k.TABLE_NAME = ?
                          AND k.COLUMN_NAME = ?
                          AND k.REFERENCED_TABLE_NAME = 'users'
                          AND k.REFERENCED_COLUMN_NAME = 'id'
                        """,
                table,
                column);
    }
}
