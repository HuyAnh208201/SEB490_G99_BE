package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Single-ship dispatch: remove legacy batch-only {@code vehicle} column from dispatch_orders.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(2)
public class SingleDispatchMigration {

    private static final Logger log = LoggerFactory.getLogger(SingleDispatchMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        dropColumnIfExists("dispatch_orders", "vehicle");
    }

    private void dropColumnIfExists(String table, String column) {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                    """,
                    Integer.class, table, column);
            if (exists != null && exists > 0) {
                jdbcTemplate.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
                log.info("Dropped {}.{} column (single-ship dispatch)", table, column);
            }
        } catch (Exception ex) {
            log.warn("dropColumn {}.{} skipped: {}", table, column, ex.getMessage());
        }
    }
}
