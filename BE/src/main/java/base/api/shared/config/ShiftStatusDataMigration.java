package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Legacy seed/SQL used statuses like {@code open}/{@code closed}. Java enum is
 * DRAFT / PUBLISHED / CANCELLED — normalize rows so JPA can load shifts.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(1)
public class ShiftStatusDataMigration {

    private static final Logger log = LoggerFactory.getLogger(ShiftStatusDataMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            int open = jdbcTemplate.update(
                    "UPDATE shifts SET status = 'PUBLISHED' WHERE LOWER(status) IN ('open', 'opened', 'active')");
            int closed = jdbcTemplate.update(
                    "UPDATE shifts SET status = 'CANCELLED' WHERE LOWER(status) IN ('closed', 'close', 'completed', 'done')");
            int draft = jdbcTemplate.update(
                    "UPDATE shifts SET status = 'DRAFT' WHERE LOWER(status) IN ('draft', 'pending')");
            int upper = jdbcTemplate.update(
                    """
                            UPDATE shifts
                            SET status = UPPER(status)
                            WHERE status IS NOT NULL
                              AND status <> UPPER(status)
                              AND UPPER(status) IN ('DRAFT', 'PUBLISHED', 'CANCELLED')
                            """);
            int unknown = jdbcTemplate.update(
                    """
                            UPDATE shifts
                            SET status = 'DRAFT'
                            WHERE status IS NULL
                               OR UPPER(status) NOT IN ('DRAFT', 'PUBLISHED', 'CANCELLED')
                            """);
            if (open + closed + draft + upper + unknown > 0) {
                log.info(
                        "Normalized shift statuses (open→PUBLISHED={}, closed→CANCELLED={}, draft={}, uppercased={}, unknown→DRAFT={})",
                        open, closed, draft, upper, unknown);
            }
        } catch (Exception ex) {
            log.warn("Shift status migration skipped: {}", ex.getMessage());
        }
    }
}
