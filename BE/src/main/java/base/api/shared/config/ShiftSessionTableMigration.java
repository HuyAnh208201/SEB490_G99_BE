package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class ShiftSessionTableMigration {

    private static final Logger log = LoggerFactory.getLogger(ShiftSessionTableMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS shift_sessions (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        shift_id BIGINT NOT NULL,
                        employee_id BIGINT NOT NULL,
                        role VARCHAR(50) NOT NULL,
                        branch_id BIGINT NOT NULL,
                        status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
                        opened_at DATETIME NULL,
                        closed_at DATETIME NULL,
                        opening_confirmed TINYINT(1) NULL DEFAULT 0,
                        verification_confirmed TINYINT(1) NULL DEFAULT 0,
                        handover_confirmed TINYINT(1) NULL DEFAULT 0,
                        opening_note TEXT NULL,
                        closing_note TEXT NULL,
                        opening_fund_amount DECIMAL(15,2) NULL,
                        opening_fund_received_from BIGINT NULL,
                        opening_fund_received_at DATETIME NULL,
                        transaction_count INT NULL DEFAULT 0,
                        cash_sales DECIMAL(15,2) NULL DEFAULT 0,
                        refund_amount DECIMAL(15,2) NULL DEFAULT 0,
                        expected_cash DECIMAL(15,2) NULL,
                        actual_cash DECIMAL(15,2) NULL,
                        difference DECIMAL(15,2) NULL,
                        handover_to_employee_id BIGINT NULL,
                        handover_remark TEXT NULL,
                        adjusted_products_count INT NULL,
                        damaged_products_count INT NULL,
                        missing_products_count INT NULL,
                        created_at DATETIME NULL,
                        updated_at DATETIME NULL,
                        PRIMARY KEY (id),
                        KEY idx_shift_session_employee (employee_id, status),
                        KEY idx_shift_session_shift (shift_id, employee_id)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS shift_session_high_value_items (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        session_id BIGINT NOT NULL,
                        product_id INT NOT NULL,
                        expected_qty INT NOT NULL DEFAULT 0,
                        actual_qty INT NULL,
                        difference INT NULL,
                        PRIMARY KEY (id),
                        KEY idx_hv_session (session_id)
                    )
                    """);
            dedupeShiftSessions();
            ensureUniqueShiftEmployee();
            log.info("Ensured shift_sessions tables");
        } catch (Exception ex) {
            log.warn("shift session migration skipped: {}", ex.getMessage());
        }
    }

    /** Keep newest row per (shift_id, employee_id) — fixes JPA NonUniqueResultException. */
    private void dedupeShiftSessions() {
        try {
            int removed = jdbcTemplate.update("""
                    DELETE s1 FROM shift_sessions s1
                    INNER JOIN shift_sessions s2
                      ON s1.shift_id = s2.shift_id
                     AND s1.employee_id = s2.employee_id
                     AND s1.id < s2.id
                    """);
            if (removed > 0) {
                log.info("Removed {} duplicate shift_sessions row(s)", removed);
            }
        } catch (Exception ex) {
            log.warn("shift_sessions dedupe skipped: {}", ex.getMessage());
        }
    }

    private void ensureUniqueShiftEmployee() {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'shift_sessions'
                      AND INDEX_NAME = 'uq_shift_session_shift_employee'
                    """,
                    Integer.class);
            if (exists == null || exists == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE shift_sessions ADD UNIQUE INDEX uq_shift_session_shift_employee (shift_id, employee_id)");
            }
        } catch (Exception ex) {
            log.warn("shift_sessions unique index skipped: {}", ex.getMessage());
        }
    }
}
