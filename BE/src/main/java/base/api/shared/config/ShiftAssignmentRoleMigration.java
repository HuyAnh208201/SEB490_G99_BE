package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adds {@code shift_assignments.assigned_role} so each assignment records which
 * role slot (Cashier / Inventory Staff) the employee was assigned to fill, and
 * backfills existing rows from the staff member's account role.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(1)
public class ShiftAssignmentRoleMigration {

    private static final Logger log = LoggerFactory.getLogger(ShiftAssignmentRoleMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            addColumnIfMissing("shift_assignments", "assigned_role", "VARCHAR(50) NULL");
            int backfilled = jdbcTemplate.update(
                    """
                            UPDATE shift_assignments sa
                            JOIN users u ON u.id = sa.staff_id
                            JOIN roles r ON r.id = u.role_id
                            SET sa.assigned_role = r.name
                            WHERE sa.assigned_role IS NULL
                            """);
            if (backfilled > 0) {
                log.info("Backfilled assigned_role on {} shift assignment(s)", backfilled);
            }
        } catch (Exception ex) {
            log.warn("Shift assignment role migration skipped: {}", ex.getMessage());
        }
    }

    private void addColumnIfMissing(String table, String column, String definition) {
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
        if (exists == null || exists == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }
}
