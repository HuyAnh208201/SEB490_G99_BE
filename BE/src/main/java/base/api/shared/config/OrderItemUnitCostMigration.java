package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Always-on: snapshot import cost onto POS order lines for profit reports. */
@Component
@Order(3)
public class OrderItemUnitCostMigration {

    private static final Logger log = LoggerFactory.getLogger(OrderItemUnitCostMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        addColumnIfMissing("order_items", "unit_cost", "DECIMAL(15,2) NULL AFTER unit_price");
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
