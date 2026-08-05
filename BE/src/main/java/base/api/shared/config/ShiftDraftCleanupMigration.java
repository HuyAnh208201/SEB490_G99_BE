package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time cleanup of DRAFT shifts corrupted by the old FE UTC serialization,
 * whose start_time and end_time landed on different calendar days (no valid
 * shift is overnight). Only unpublished DRAFT rows are touched, so real data is
 * safe; the check is idempotent and a no-op once clean.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(1)
public class ShiftDraftCleanupMigration {

    private static final Logger log = LoggerFactory.getLogger(ShiftDraftCleanupMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            int assignments = jdbcTemplate.update(
                    """
                            DELETE sa FROM shift_assignments sa
                            JOIN shifts s ON s.id = sa.shift_id
                            WHERE UPPER(s.status) = 'DRAFT'
                              AND DATE(s.start_time) <> DATE(s.end_time)
                            """);
            int shifts = jdbcTemplate.update(
                    """
                            DELETE FROM shifts
                            WHERE UPPER(status) = 'DRAFT'
                              AND DATE(start_time) <> DATE(end_time)
                            """);
            if (shifts > 0 || assignments > 0) {
                log.info("Cleaned up {} corrupted DRAFT shift(s) and {} assignment(s)", shifts, assignments);
            }
        } catch (Exception ex) {
            log.warn("Shift draft cleanup skipped: {}", ex.getMessage());
        }
    }
}
