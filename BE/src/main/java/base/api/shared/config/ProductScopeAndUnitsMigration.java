package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adds product scope columns, unit tables, and normalizes legacy Vietnamese unit slugs to English.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(1)
public class ProductScopeAndUnitsMigration {

    private static final Logger log = LoggerFactory.getLogger(ProductScopeAndUnitsMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            ensureProductColumns();
            ensureUnitTables();
            normalizeProductUnits();
            seedUnitsIfEmpty();
            log.info("Product scope and unit migration completed");
        } catch (Exception ex) {
            log.warn("Product scope and unit migration skipped: {}", ex.getMessage());
        }
    }

    private void ensureProductColumns() {
        addColumnIfMissing("products", "scope", "VARCHAR(16) NOT NULL DEFAULT 'GLOBAL'");
        addColumnIfMissing("products", "branch_id", "BIGINT NULL");
        addColumnIfMissing("products", "import_unit", "VARCHAR(64) NULL");
        addColumnIfMissing("products", "units_per_import_unit", "INT NULL");
        addColumnIfMissing("products", "refundable", "TINYINT(1) NOT NULL DEFAULT 1");
        jdbcTemplate.execute("UPDATE products SET scope = 'GLOBAL' WHERE scope IS NULL OR scope = ''");
    }

    private void ensureUnitTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS units_of_measure (
                    id INT NOT NULL AUTO_INCREMENT,
                    code VARCHAR(32) NOT NULL,
                    label_en VARCHAR(64) NOT NULL,
                    unit_type VARCHAR(16) NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uq_units_code_type (code, unit_type)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS critical_user_action_tokens (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    target_user_id BIGINT NOT NULL,
                    actor_user_id BIGINT NOT NULL,
                    action_type VARCHAR(32) NOT NULL,
                    verification_code VARCHAR(6) NOT NULL,
                    expires_at DATETIME NOT NULL,
                    used TINYINT(1) NOT NULL DEFAULT 0,
                    created_at DATETIME NOT NULL,
                    PRIMARY KEY (id)
                )
                """);
    }

    private void seedUnitsIfEmpty() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM units_of_measure", Integer.class);
        if (count != null && count > 0) {
            return;
        }

        String[][] retail = {
                {"piece", "Piece (pcs)"},
                {"bottle", "Bottle"},
                {"can", "Can"},
                {"pack", "Pack"},
                {"box", "Box"},
                {"bag", "Bag"},
                {"blister", "Blister pack"},
                {"roll", "Roll"},
                {"pair", "Pair"},
                {"kg", "Kilogram (kg)"},
                {"gram", "Gram (g)"},
                {"liter", "Liter (L)"},
                {"ml", "Milliliter (ml)"},
        };
        String[][] purchase = {
                {"case", "Case"},
                {"carton", "Carton"},
                {"crate", "Crate"},
                {"pallet", "Pallet"},
                {"lot", "Lot"},
                {"bundle", "Bundle"},
                {"sack", "Sack"},
                {"box", "Box (wholesale)"},
        };

        for (String[] row : retail) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO units_of_measure (code, label_en, unit_type) VALUES (?, ?, 'RETAIL')",
                    row[0], row[1]);
        }
        for (String[] row : purchase) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO units_of_measure (code, label_en, unit_type) VALUES (?, ?, 'PURCHASE')",
                    row[0], row[1]);
        }
    }

    private void normalizeProductUnits() {
        String[][] mappings = {
                {"cai", "piece"}, {"chai", "bottle"}, {"lon", "can"}, {"goi", "pack"},
                {"hop", "box"}, {"thung", "case"}, {"vi", "blister"}, {"tui", "bag"},
                {"cuon", "roll"}, {"cai_doi", "pair"}, {"lit", "liter"}, {"gram", "gram"},
                {"bao", "sack"},
        };
        for (String[] map : mappings) {
            jdbcTemplate.update("UPDATE products SET unit = ? WHERE LOWER(unit) = ?", map[1], map[0]);
        }

        jdbcTemplate.update("""
                UPDATE products p
                SET import_unit = CASE LOWER(p.unit)
                    WHEN 'can' THEN 'case'
                    WHEN 'bottle' THEN 'case'
                    WHEN 'pack' THEN 'carton'
                    WHEN 'box' THEN 'carton'
                    WHEN 'piece' THEN 'carton'
                    WHEN 'bag' THEN 'bundle'
                    ELSE 'case'
                END,
                units_per_import_unit = CASE LOWER(p.unit)
                    WHEN 'can' THEN 24
                    WHEN 'bottle' THEN 24
                    WHEN 'pack' THEN 30
                    WHEN 'box' THEN 12
                    WHEN 'piece' THEN 20
                    WHEN 'bag' THEN 10
                    ELSE 24
                END
                WHERE import_unit IS NULL
                """);
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
