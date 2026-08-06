package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adds the composite indexes used by weekly schedule and assignment lookups.
 * The migration is idempotent because production disables Hibernate DDL.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(2)
public class ShiftPerformanceIndexMigration {

    private static final Logger log = LoggerFactory.getLogger(ShiftPerformanceIndexMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            addIndexIfMissing(
                    "shifts",
                    "idx_shifts_branch_start_end",
                    "CREATE INDEX idx_shifts_branch_start_end ON shifts (branch_id, start_time, end_time)");
            addIndexIfMissing(
                    "shift_assignments",
                    "idx_shift_assignments_shift_staff",
                    "CREATE INDEX idx_shift_assignments_shift_staff ON shift_assignments (shift_id, staff_id)");
            addIndexIfMissing(
                    "users",
                    "idx_users_branch_role",
                    "CREATE INDEX idx_users_branch_role ON users (branch_id, role_id)");
        } catch (Exception ex) {
            log.warn("Shift performance index migration skipped: {}", ex.getMessage());
        }
    }

    private void addIndexIfMissing(String table, String index, String statement) {
        Integer exists = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                          AND INDEX_NAME = ?
                        """,
                Integer.class,
                table,
                index);
        if (exists == null || exists == 0) {
            jdbcTemplate.execute(statement);
            log.info("Created performance index {} on {}", index, table);
        }
    }
}
