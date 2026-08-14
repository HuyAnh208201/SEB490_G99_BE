package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Adds Module F workflow fields exactly once on databases that do not have them yet. */
@Component
@ConditionalOnStartupBootstrap
@Order(0)
public class BranchReceivingWorkflowMigration {
    private static final Logger log = LoggerFactory.getLogger(BranchReceivingWorkflowMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        addColumnIfMissing("dispatch_orders", "recipient_id", "BIGINT NULL AFTER created_by");
        addColumnIfMissing("dispatch_orders", "shipped_at", "DATETIME NULL AFTER recipient_id");
        addColumnIfMissing("purchase_requests", "submitted_at", "DATETIME NULL AFTER created_at");
        addColumnIfMissing("purchase_requests", "desired_receive_date", "DATE NULL AFTER submitted_at");
        addColumnIfMissing("purchase_requests", "supplemental_for_receipt_id", "BIGINT NULL AFTER desired_receive_date");
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                    """, Integer.class, table, column);
            if (count == null || count == 0) {
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                log.info("Added Module F column {}.{}", table, column);
            }
        } catch (Exception ex) {
            log.warn("Module F column {}.{} skipped: {}", table, column, ex.getMessage());
        }
    }
}
