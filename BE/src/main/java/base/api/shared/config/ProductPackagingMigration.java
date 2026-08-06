package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ensures {@code product_packagings} has the columns needed for ordered UoM
 * levels (code, label_en, is_purchase_default, sort_order), translates
 * legacy Vietnamese packaging names to English display labels, flags the
 * base (level 1) and TOP/purchase-default (last level) rows per product,
 * and backfills a base + top packaging for any product that has none yet
 * (falling back to products.import_unit / units_per_import_unit).
 *
 * Packaging is the source of truth for purchase-request quantities; the
 * legacy import_unit / units_per_import_unit columns on products are kept
 * only as a compatibility fallback for products without packaging rows.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(3)
public class ProductPackagingMigration {

    private static final Logger log = LoggerFactory.getLogger(ProductPackagingMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            ensureColumns();
            backfillCodesAndLabels();
            backfillSortOrder();
            backfillLevelFlags();
            backfillMissingPackagings();
            log.info("Product packaging migration completed");
        } catch (Exception ex) {
            log.warn("Product packaging migration skipped: {}", ex.getMessage());
        }
    }

    private void ensureColumns() {
        addColumnIfMissing("product_packagings", "code", "VARCHAR(64) NULL");
        addColumnIfMissing("product_packagings", "label_en", "VARCHAR(128) NULL");
        addColumnIfMissing("product_packagings", "is_purchase_default", "TINYINT(1) NOT NULL DEFAULT 0");
        addColumnIfMissing("product_packagings", "sort_order", "INT NOT NULL DEFAULT 0");
    }

    private void backfillCodesAndLabels() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, name, conversion_qty, is_base FROM product_packagings "
                        + "WHERE label_en IS NULL OR label_en = '' OR code IS NULL OR code = ''");
        for (Map<String, Object> row : rows) {
            Number id = (Number) row.get("id");
            String name = (String) row.get("name");
            Number conversionQtyRaw = (Number) row.get("conversion_qty");
            int conversionQty = conversionQtyRaw == null ? 1 : conversionQtyRaw.intValue();
            boolean isBase = toBool(row.get("is_base"));

            String label = translateLabel(name, conversionQty, isBase);
            String code = slugify(label, conversionQty, isBase);
            jdbcTemplate.update(
                    "UPDATE product_packagings SET label_en = ?, code = ? WHERE id = ?",
                    label, code, id.intValue());
        }
    }

    private void backfillSortOrder() {
        jdbcTemplate.update(
                "UPDATE product_packagings "
                        + "SET sort_order = CASE WHEN is_base = 1 THEN 0 ELSE COALESCE(conversion_qty, 1) END");
    }

    private void backfillLevelFlags() {
        // Level-1 base = smallest conversion_qty per product.
        jdbcTemplate.update("""
                UPDATE product_packagings pp
                JOIN (
                    SELECT product_id, MIN(conversion_qty) AS min_qty
                    FROM product_packagings
                    GROUP BY product_id
                ) base_level
                  ON pp.product_id = base_level.product_id AND pp.conversion_qty = base_level.min_qty
                SET pp.is_base = 1
                WHERE pp.is_base = 0
                """);

        // TOP level (used by purchase requests) = largest conversion_qty per product.
        jdbcTemplate.update("UPDATE product_packagings SET is_purchase_default = 0");
        jdbcTemplate.update("""
                UPDATE product_packagings pp
                JOIN (
                    SELECT product_id, MAX(conversion_qty) AS max_qty
                    FROM product_packagings
                    GROUP BY product_id
                ) top_level
                  ON pp.product_id = top_level.product_id AND pp.conversion_qty = top_level.max_qty
                SET pp.is_purchase_default = 1
                """);
    }

    private void backfillMissingPackagings() {
        List<Map<String, Object>> orphanProducts = jdbcTemplate.queryForList("""
                SELECT p.id, p.unit, p.import_unit, p.units_per_import_unit
                FROM products p
                LEFT JOIN product_packagings pp ON pp.product_id = p.id
                WHERE pp.id IS NULL
                """);

        for (Map<String, Object> row : orphanProducts) {
            Number productId = (Number) row.get("id");
            String unit = (String) row.get("unit");
            String importUnit = (String) row.get("import_unit");
            Number unitsPerImportUnitRaw = (Number) row.get("units_per_import_unit");
            int unitsPerImportUnit = unitsPerImportUnitRaw == null ? 1 : unitsPerImportUnitRaw.intValue();

            String baseLabel = capitalize(unit == null ? "piece" : unit);
            boolean singleLevel = unitsPerImportUnit <= 1 || importUnit == null;

            jdbcTemplate.update(
                    "INSERT INTO product_packagings "
                            + "(product_id, code, name, label_en, conversion_qty, is_base, is_purchase_default, sort_order) "
                            + "VALUES (?, 'base', ?, ?, 1, 1, ?, 0)",
                    productId.intValue(), baseLabel, baseLabel, singleLevel ? 1 : 0);

            if (!singleLevel) {
                String topLabel = capitalize(importUnit) + " of " + unitsPerImportUnit;
                jdbcTemplate.update(
                        "INSERT INTO product_packagings "
                                + "(product_id, code, name, label_en, conversion_qty, is_base, is_purchase_default, sort_order) "
                                + "VALUES (?, 'top', ?, ?, ?, 0, 1, ?)",
                        productId.intValue(), topLabel, topLabel, unitsPerImportUnit, unitsPerImportUnit);
            }
        }
    }

    private String translateLabel(String rawName, int conversionQty, boolean isBase) {
        String name = rawName == null ? "" : rawName.trim();
        String lower = name.toLowerCase(Locale.ROOT);

        switch (lower) {
            case "chai": return "Bottle";
            case "lon": return "Can";
            case "goi", "gói": return "Pack";
            case "hop", "hộp": return "Box";
            case "cai", "cái": return "Piece";
            case "vi", "vỉ": return "Blister pack";
            case "tui", "túi": return "Bag";
            case "cuon", "cuộn": return "Roll";
            default:
                break;
        }
        if (lower.startsWith("thung") || lower.startsWith("thùng")) {
            return "Case of " + conversionQty;
        }
        if (lower.startsWith("loc") || lower.startsWith("lốc")) {
            return "Pack of " + conversionQty;
        }
        if (!name.isBlank() && name.matches("^[\\x00-\\x7F]+$")) {
            return name;
        }
        return isBase ? "Unit" : ("Case of " + conversionQty);
    }

    private String slugify(String label, int conversionQty, boolean isBase) {
        if (isBase) {
            return "base";
        }
        String base = label == null ? "top" : label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        base = base.replaceAll("(^-|-$)", "");
        return (base.isBlank() ? "top" : base) + "-" + conversionQty;
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Unit";
        }
        String trimmed = value.trim();
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1).toLowerCase(Locale.ROOT);
    }

    private boolean toBool(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return "1".equals(String.valueOf(value)) || "true".equalsIgnoreCase(String.valueOf(value));
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
