package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adds category active/short_date flags and dispatch_order_suppliers for supplier-direct
 * short-date shipping (no central warehouse stock).
 *
 * Always runs (even when app.startup.bootstrap-enabled=false) — entities require these columns.
 */
@Component
@Order(8)
public class ShortDateCategorySchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(ShortDateCategorySchemaMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            addColumnIfMissing("categories", "active", "TINYINT(1) NOT NULL DEFAULT 1");
            addColumnIfMissing("categories", "short_date", "TINYINT(1) NOT NULL DEFAULT 0");
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS dispatch_order_suppliers (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        dispatch_order_id BIGINT NOT NULL,
                        supplier_id INT NOT NULL,
                        PRIMARY KEY (id),
                        UNIQUE KEY uq_dispatch_supplier (dispatch_order_id, supplier_id),
                        KEY idx_dos_dispatch (dispatch_order_id),
                        KEY idx_dos_supplier (supplier_id)
                    )
                    """);
            log.info("Ensured categories.active/short_date and dispatch_order_suppliers");
        } catch (Exception ex) {
            log.error("short-date category schema migration failed: {}", ex.getMessage(), ex);
            throw new IllegalStateException(
                    "Required short-date schema (categories.active/short_date, dispatch_order_suppliers) could not be applied",
                    ex);
        }
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
            }
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to ensure column " + table + "." + column + ": " + ex.getMessage(), ex);
        }
    }
}
