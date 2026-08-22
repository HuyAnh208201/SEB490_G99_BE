package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Retires the {@code vouchers} table — discount codes are no longer used at POS checkout.
 * Drops FK/column on {@code order_discounts} first, then the table itself.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(1)
public class DropVouchersTableMigration {

    private static final Logger log = LoggerFactory.getLogger(DropVouchersTableMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            dropForeignKeyIfPresent("order_discounts", "voucher_id", "vouchers");
            dropColumnIfExists("order_discounts", "voucher_id");
            dropTableIfExists("vouchers");
            log.info("Ensured vouchers table is removed");
        } catch (Exception ex) {
            log.warn("Drop vouchers migration skipped: {}", ex.getMessage());
        }
    }

    private void dropForeignKeyIfPresent(String table, String column, String referencedTable) {
        String constraintName = jdbcTemplate.query(
                """
                        SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                          AND COLUMN_NAME = ?
                          AND REFERENCED_TABLE_NAME = ?
                        LIMIT 1
                        """,
                rs -> rs.next() ? rs.getString(1) : null,
                table,
                column,
                referencedTable);
        if (constraintName != null) {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP FOREIGN KEY " + constraintName);
            log.info("Dropped FK {} on {}.{}", constraintName, table, column);
        }
    }

    private void dropColumnIfExists(String table, String column) {
        Integer exists = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                          AND COLUMN_NAME = ?
                        """,
                Integer.class,
                table,
                column);
        if (exists != null && exists > 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
            log.info("Dropped column {}.{}", table, column);
        }
    }

    private void dropTableIfExists(String table) {
        Integer exists = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.TABLES
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                        """,
                Integer.class,
                table);
        if (exists != null && exists > 0) {
            jdbcTemplate.execute("DROP TABLE " + table);
            log.info("Dropped table {}", table);
        }
    }
}
