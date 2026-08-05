package base.api.shared.config;

import base.api.shared.util.Ean13BarcodeGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seeds the curated Vietnam Top-200 convenience catalog from classpath JSON,
 * deactivates known duplicate premium SKUs, remaps leftover synthetic barcodes,
 * and ensures packaging + inventory rows.
 *
 * <p>Demo catalog barcodes use valid EAN-13 (893…). Not a claim of GS1 ownership.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(5)
public class VietnamTop200CatalogSeedMigration {

    private static final Logger log = LoggerFactory.getLogger(VietnamTop200CatalogSeedMigration.class);
    private static final String CATALOG_RESOURCE = "catalog/vn_top200_products.json";

    /** Older twins of TOB/COS/ALC/CRD keep-set (same display names, different codes). */
    private static final String[] DUPLICATE_CODES_TO_DEACTIVATE = {
            "SP0000415",
            "893000000017",
            "CVS-DRK-001",
            "CVS-DRK-002",
            "CVS-DRK-003",
            "CVS-DRK-004",
            "CVS-DRK-005",
            "CVS-DRK-006",
            "CVS-DRK-007",
            "CVS-DRK-008",
            "CVS-DRK-009",
            "CVS-DRK-010",
            "CVS-DRK-011",
            "CVS-DRK-012",
            "CVS-DRK-013",
            "CVS-DRK-014",
    };

    private static final String[] EXTRA_CATEGORIES = {
            "Cosmetics",
            "Top-up / Digital",
            "Alcohol",
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void migrate() {
        try {
            List<CatalogProduct> catalog = loadCatalog();
            if (catalog.isEmpty()) {
                log.warn("Vietnam Top-200 catalog JSON empty — skipped");
                return;
            }

            ensureCategories();
            ensureExtraUnits();
            deactivateDuplicateSkus();

            Map<String, Integer> categoryIds = loadCategoryIds();
            Set<String> catalogCodes = new HashSet<>();
            for (CatalogProduct p : catalog) {
                catalogCodes.add(p.code());
                upsertProduct(p, categoryIds);
            }

            ensurePackagingAndInventory(catalogCodes);
            deactivateNameCollisions(catalogCodes);
            remapSyntheticBarcodes(catalogCodes);

            log.info("Vietnam Top-200 catalog seed ensured ({} SKUs)", catalog.size());
        } catch (Exception ex) {
            log.warn("Vietnam Top-200 catalog seed skipped: {}", ex.getMessage(), ex);
        }
    }

    private List<CatalogProduct> loadCatalog() throws Exception {
        ClassPathResource resource = new ClassPathResource(CATALOG_RESOURCE);
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<CatalogProduct>>() {
            });
        }
    }

    private void ensureCategories() {
        String[] base = {
                "Drinks", "Instant food", "Dairy", "Snacks", "Personal care",
                "Household", "Tobacco & Beer", "Ice cream", "Bakery",
        };
        for (String name : base) {
            insertCategoryIfMissing(name);
        }
        for (String name : EXTRA_CATEGORIES) {
            insertCategoryIfMissing(name);
        }
    }

    private void insertCategoryIfMissing(String name) {
        jdbcTemplate.update(
                """
                        INSERT INTO categories (name, parent_id, description)
                        SELECT ?, NULL, ?
                        WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = ?)
                        """,
                name, name + " products for convenience stores", name);
    }

    private void ensureExtraUnits() {
        String[][] extra = {
                {"jar", "Jar"},
                {"card", "Card"},
                {"carton", "Carton"},
        };
        for (String[] row : extra) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO units_of_measure (code, label_en, unit_type) VALUES (?, ?, 'RETAIL')",
                    row[0], row[1]);
        }
    }

    private Map<String, Integer> loadCategoryIds() {
        Map<String, Integer> map = new HashMap<>();
        jdbcTemplate.query("SELECT id, name FROM categories", rs -> {
            map.put(rs.getString("name"), rs.getInt("id"));
        });
        return map;
    }

    private void deactivateDuplicateSkus() {
        int updated = 0;
        for (String code : DUPLICATE_CODES_TO_DEACTIVATE) {
            updated += jdbcTemplate.update(
                    "UPDATE products SET status = 'inactive' WHERE code = ? AND status <> 'inactive'",
                    code);
        }
        log.info("Deactivated {} duplicate premium SKU row(s)", updated);
    }

    private void upsertProduct(CatalogProduct p, Map<String, Integer> categoryIds) {
        Integer categoryId = categoryIds.get(p.category());
        if (categoryId == null) {
            insertCategoryIfMissing(p.category());
            categoryIds.putAll(loadCategoryIds());
            categoryId = categoryIds.get(p.category());
        }

        Integer existingId = queryInteger("SELECT id FROM products WHERE code = ?", p.code());
        freeBarcodeIfOwnedByOther(p.barcode(), existingId);

        if (existingId == null) {
            jdbcTemplate.update(
                    """
                            INSERT INTO products
                                (code, barcode, name, category_id, unit, import_unit, units_per_import_unit,
                                 reference_import_price, default_sale_price, scope, status, description)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'GLOBAL', 'active', ?)
                            """,
                    p.code(),
                    p.barcode(),
                    p.name(),
                    categoryId,
                    p.unit(),
                    p.importUnit(),
                    p.unitsPerImportUnit(),
                    p.referenceImportPrice(),
                    p.defaultSalePrice(),
                    p.description() == null || p.description().isBlank() ? p.name() : p.description());
        } else {
            jdbcTemplate.update(
                    """
                            UPDATE products
                            SET barcode = ?,
                                name = ?,
                                category_id = ?,
                                unit = ?,
                                import_unit = ?,
                                units_per_import_unit = ?,
                                reference_import_price = ?,
                                default_sale_price = ?,
                                scope = 'GLOBAL',
                                status = 'active',
                                description = ?,
                                branch_id = NULL
                            WHERE id = ?
                            """,
                    p.barcode(),
                    p.name(),
                    categoryId,
                    p.unit(),
                    p.importUnit(),
                    p.unitsPerImportUnit(),
                    p.referenceImportPrice(),
                    p.defaultSalePrice(),
                    p.description() == null || p.description().isBlank() ? p.name() : p.description(),
                    existingId);
        }

        Integer productId = queryInteger("SELECT id FROM products WHERE code = ?", p.code());
        if (productId != null) {
            ensureBaseAndCasePackaging(productId, p);
        }
    }

    private void freeBarcodeIfOwnedByOther(String barcode, Integer keepProductId) {
        if (barcode == null || barcode.isBlank()) {
            return;
        }
        List<Integer> owners = jdbcTemplate.query(
                "SELECT id FROM products WHERE barcode = ?",
                (rs, rowNum) -> rs.getInt(1),
                barcode);
        for (Integer ownerId : owners) {
            if (keepProductId != null && ownerId.equals(keepProductId)) {
                continue;
            }
            jdbcTemplate.update("UPDATE products SET barcode = NULL WHERE id = ?", ownerId);
        }
    }

    private void ensureBaseAndCasePackaging(int productId, CatalogProduct p) {
        insertPackagingIfMissing(productId, p.unit(), 1, p.barcode(), true, 0, false);
        if (p.unitsPerImportUnit() != null && p.unitsPerImportUnit() > 1 && p.importUnit() != null) {
            String caseBarcode = deriveCaseBarcode(p.barcode(), productId);
            insertPackagingIfMissing(
                    productId, p.importUnit(), p.unitsPerImportUnit(), caseBarcode, false, 1, true);
        }
        // Keep base packaging barcode in sync with product barcode
        jdbcTemplate.update(
                """
                        UPDATE product_packagings
                        SET barcode = ?
                        WHERE product_id = ? AND is_base = 1
                        """,
                p.barcode(), productId);
    }

    private String deriveCaseBarcode(String baseBarcode, int productId) {
        if (baseBarcode != null && baseBarcode.matches("\\d{13}")) {
            // Deterministic sibling: flip middle digit region using product id — still valid check digit
            List<String> existing = loadAllBarcodes();
            return Ean13BarcodeGenerator.nextBarcode(existing);
        }
        List<String> existing = loadAllBarcodes();
        return Ean13BarcodeGenerator.nextBarcode(existing);
    }

    private void insertPackagingIfMissing(
            int productId,
            String name,
            int conversionQty,
            String barcode,
            boolean isBase,
            int sortOrder,
            boolean isPurchaseDefault) {
        jdbcTemplate.update(
                """
                        INSERT INTO product_packagings
                            (product_id, name, conversion_qty, barcode, is_base, sort_order, is_purchase_default)
                        SELECT ?, ?, ?, ?, ?, ?, ?
                        WHERE NOT EXISTS (
                            SELECT 1 FROM product_packagings WHERE product_id = ? AND name = ?
                        )
                        """,
                productId, name, conversionQty, barcode, isBase, sortOrder, isPurchaseDefault,
                productId, name);
    }

    private void ensurePackagingAndInventory(Set<String> catalogCodes) {
        seedWarehouseForCodes(catalogCodes);
        seedBranchForCodes(catalogCodes);
    }

    private void seedWarehouseForCodes(Set<String> catalogCodes) {
        jdbcTemplate.execute(
                """
                        CREATE TABLE IF NOT EXISTS warehouse_inventory (
                            id BIGINT NOT NULL AUTO_INCREMENT,
                            product_id INT NOT NULL,
                            quantity INT NOT NULL DEFAULT 0,
                            reorder_point INT NOT NULL DEFAULT 0,
                            updated_at DATETIME NULL,
                            PRIMARY KEY (id),
                            UNIQUE KEY uq_warehouse_inventory_product (product_id)
                        )
                        """);

        for (String code : catalogCodes) {
            Integer productId = queryInteger("SELECT id FROM products WHERE code = ?", code);
            if (productId == null) {
                continue;
            }
            jdbcTemplate.update(
                    """
                            INSERT INTO warehouse_inventory (product_id, quantity, reorder_point, updated_at)
                            SELECT p.id,
                                   CASE WHEN (p.id % 11) = 0
                                        THEN GREATEST(IFNULL(p.units_per_import_unit, 1), 1)
                                        ELSE GREATEST(IFNULL(p.units_per_import_unit, 1), 1) * 8
                                   END,
                                   GREATEST(IFNULL(p.units_per_import_unit, 1), 1) * 2,
                                   NOW()
                            FROM products p
                            WHERE p.id = ?
                              AND NOT EXISTS (SELECT 1 FROM warehouse_inventory w WHERE w.product_id = p.id)
                            """,
                    productId);
        }
    }

    private void seedBranchForCodes(Set<String> catalogCodes) {
        ensureBranchInventoryUniqueKey();
        for (String code : catalogCodes) {
            Integer productId = queryInteger("SELECT id FROM products WHERE code = ?", code);
            if (productId == null) {
                continue;
            }
            jdbcTemplate.update(
                    """
                            INSERT INTO branch_inventory (branch_id, product_id, quantity, reorder_point, updated_at)
                            SELECT b.id,
                                   ?,
                                   CASE WHEN (? % 9) = 0 THEN 5 ELSE 20 + (? % 25) END,
                                   10,
                                   NOW()
                            FROM branches b
                            WHERE NOT EXISTS (
                                SELECT 1 FROM branch_inventory bi
                                WHERE bi.branch_id = b.id AND bi.product_id = ?
                            )
                            """,
                    productId, productId, productId, productId);
        }
    }

    private void ensureBranchInventoryUniqueKey() {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*) FROM information_schema.STATISTICS
                            WHERE TABLE_SCHEMA = DATABASE()
                              AND TABLE_NAME = 'branch_inventory'
                              AND INDEX_NAME = 'uq_branch_inventory_branch_product'
                            """,
                    Integer.class);
            if (exists == null || exists == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE branch_inventory ADD UNIQUE KEY uq_branch_inventory_branch_product (branch_id, product_id)");
            }
        } catch (Exception ex) {
            log.warn("branch_inventory unique key ensure skipped: {}", ex.getMessage());
        }
    }

    /**
     * Deactivate active products that share an exact name with a Top-200 SKU but are not in the catalog.
     */
    private void deactivateNameCollisions(Set<String> catalogCodes) {
        int n = jdbcTemplate.update(
                """
                        UPDATE products p
                        INNER JOIN products keep
                          ON keep.name = p.name
                         AND keep.status = 'active'
                         AND keep.code IN (%s)
                        SET p.status = 'inactive'
                        WHERE p.status = 'active'
                          AND p.code NOT IN (%s)
                        """.formatted(inClause(catalogCodes), inClause(catalogCodes)));
        if (n > 0) {
            log.info("Deactivated {} active name-collision SKU(s) outside Top-200", n);
        }
    }

    private void remapSyntheticBarcodes(Set<String> catalogCodes) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        SELECT id, code, barcode FROM products
                        WHERE status = 'active'
                          AND code NOT IN (%s)
                        """.formatted(inClause(catalogCodes)));

        List<String> existing = loadAllBarcodes();
        int remapped = 0;
        for (Map<String, Object> row : rows) {
            Integer id = ((Number) row.get("id")).intValue();
            String barcode = row.get("barcode") == null ? null : String.valueOf(row.get("barcode"));
            if (!needsRemap(barcode)) {
                continue;
            }
            String next = Ean13BarcodeGenerator.nextBarcode(existing);
            existing.add(next);
            jdbcTemplate.update("UPDATE products SET barcode = ? WHERE id = ?", next, id);
            jdbcTemplate.update(
                    "UPDATE product_packagings SET barcode = ? WHERE product_id = ? AND is_base = 1",
                    next, id);
            remapped++;
        }
        if (remapped > 0) {
            log.info("Remapped {} synthetic/missing barcodes on leftover active SKUs", remapped);
        }
    }

    private static boolean needsRemap(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return true;
        }
        if (!barcode.matches("\\d{13}")) {
            return true;
        }
        // Legacy ConvenienceStoreCatalogSeedMigration ranges
        return barcode.startsWith("8939")
                || barcode.startsWith("8938")
                || barcode.startsWith("8937")
                || barcode.startsWith("8931");
    }

    private List<String> loadAllBarcodes() {
        List<String> list = new ArrayList<>();
        jdbcTemplate.query("SELECT barcode FROM products WHERE barcode IS NOT NULL", rs -> {
            list.add(rs.getString(1));
        });
        jdbcTemplate.query("SELECT barcode FROM product_packagings WHERE barcode IS NOT NULL", rs -> {
            list.add(rs.getString(1));
        });
        return list;
    }

    private Integer queryInteger(String sql, Object... args) {
        List<Integer> rows = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt(1), args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String inClause(Set<String> codes) {
        StringBuilder sb = new StringBuilder();
        for (String code : codes) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append('\'').append(code.replace("'", "''")).append('\'');
        }
        return sb.length() == 0 ? "''" : sb.toString();
    }

    public record CatalogProduct(
            String code,
            String barcode,
            String name,
            String category,
            String unit,
            String importUnit,
            Integer unitsPerImportUnit,
            BigDecimal referenceImportPrice,
            BigDecimal defaultSalePrice,
            String description) {
    }
}
