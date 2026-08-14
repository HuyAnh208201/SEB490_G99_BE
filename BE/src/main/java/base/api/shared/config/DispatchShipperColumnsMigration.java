package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Always-on: shipper contact on warehouse→branch dispatches (any machine, bootstrap on or off). */
@Component
@Order(3)
public class DispatchShipperColumnsMigration {

    private static final Logger log = LoggerFactory.getLogger(DispatchShipperColumnsMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        addColumnIfMissing("dispatch_orders", "shipper_name", "VARCHAR(150) NULL AFTER recipient_id");
        addColumnIfMissing("dispatch_orders", "shipper_phone", "VARCHAR(20) NULL AFTER shipper_name");
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
