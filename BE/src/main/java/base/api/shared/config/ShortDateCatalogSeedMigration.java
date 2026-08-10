package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds short-date (perishable) categories and sample products that are NOT held in
 * central warehouse inventory — suppliers deliver them directly to branches.
 *
 * Idempotent; runs even when full bootstrap is off so local/shared DBs get test SKUs.
 * Depends on {@link ShortDateCategorySchemaMigration} so active/short_date columns exist first.
 */
@Component
@DependsOn("shortDateCategorySchemaMigration")
@Order(9)
public class ShortDateCatalogSeedMigration {

    private static final Logger log = LoggerFactory.getLogger(ShortDateCatalogSeedMigration.class);
    private static final String CODE_PREFIX = "SD-";

    private static final String[] SHORT_DATE_CATEGORIES = {
            "Frozen Food",
            "Ready-to-eat",
            "Fresh Food",
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            ensureCategories();
            markShortDateFlags();
            Map<String, Integer> categoryIds = loadCategoryIds();
            seedProducts(categoryIds);
            purgeWarehouseInventoryForShortDate();
            ensureDemoSuppliers();
            log.info("Short-date catalog seed ensured ({} categories)", SHORT_DATE_CATEGORIES.length);
        } catch (Exception ex) {
            log.warn("Short-date catalog seed skipped: {}", ex.getMessage());
        }
    }

    private void ensureCategories() {
        String[][] defs = {
                {"Frozen Food", "Frozen goods with short shelf life (ice cream, frozen seafood, etc.)"},
                {"Ready-to-eat", "Ready-to-eat meals with short shelf life (rice balls, sandwiches, etc.)"},
                {"Fresh Food", "Fresh meat, seafood, and produce with short expiration dates"},
        };
        for (String[] row : defs) {
            jdbcTemplate.update(
                    """
                            INSERT INTO categories (name, parent_id, description, active, short_date)
                            SELECT ?, NULL, ?, 1, 1
                            WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = ?)
                            """,
                    row[0], row[1], row[0]);
        }
        // Units used by short-date sample SKUs
        String[][] units = {
                {"box", "Box"},
                {"bowl", "Bowl"},
                {"tub", "Tub"},
                {"cup", "Cup"},
                {"tray", "Tray (mid-pack)"},
        };
        for (String[] u : units) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO units_of_measure (code, label_en, unit_type) VALUES (?, ?, 'RETAIL')",
                    u[0], u[1]);
        }
    }

    private void markShortDateFlags() {
        for (String name : SHORT_DATE_CATEGORIES) {
            jdbcTemplate.update(
                    "UPDATE categories SET short_date = 1, active = 1 WHERE name = ?",
                    name);
        }
    }

    private Map<String, Integer> loadCategoryIds() {
        Map<String, Integer> map = new HashMap<>();
        jdbcTemplate.query("SELECT id, name FROM categories", rs -> {
            map.put(rs.getString("name"), rs.getInt("id"));
        });
        return map;
    }

    private void seedProducts(Map<String, Integer> categoryIds) {
        List<SeedProduct> products = List.of(
                new SeedProduct("FRZ-001", "Frozen Fish Fillets", "Frozen Food", "pack", "case", 10,
                        bd("85000"), bd("120000")),
                new SeedProduct("FRZ-002", "Frozen Shrimp", "Frozen Food", "pack", "case", 10,
                        bd("95000"), bd("135000")),
                new SeedProduct("FRZ-003", "Frozen Chicken Wings", "Frozen Food", "pack", "case", 12,
                        bd("78000"), bd("110000")),
                new SeedProduct("FRZ-004", "Vanilla Ice Cream Tub", "Frozen Food", "tub", "case", 6,
                        bd("45000"), bd("69000")),
                new SeedProduct("FRZ-005", "Chocolate Ice Cream Cups", "Frozen Food", "cup", "case", 24,
                        bd("12000"), bd("19000")),
                new SeedProduct("RTE-001", "Rice Ball (Onigiri)", "Ready-to-eat", "piece", "tray", 12,
                        bd("15000"), bd("25000")),
                new SeedProduct("RTE-002", "Chicken Sandwich", "Ready-to-eat", "piece", "tray", 10,
                        bd("22000"), bd("35000")),
                new SeedProduct("RTE-003", "Fresh Salad Bowl", "Ready-to-eat", "bowl", "case", 8,
                        bd("28000"), bd("42000")),
                new SeedProduct("RTE-004", "Bento Box Lunch", "Ready-to-eat", "box", "case", 6,
                        bd("35000"), bd("55000")),
                new SeedProduct("FSH-001", "Fresh Salmon Steak", "Fresh Food", "pack", "case", 8,
                        bd("110000"), bd("155000")),
                new SeedProduct("FSH-002", "Fresh Prawns", "Fresh Food", "pack", "case", 8,
                        bd("98000"), bd("140000")),
                new SeedProduct("FSH-003", "Fresh Chicken Drumsticks", "Fresh Food", "pack", "case", 10,
                        bd("65000"), bd("95000")),
                new SeedProduct("FSH-004", "Fresh Leafy Greens", "Fresh Food", "pack", "case", 12,
                        bd("18000"), bd("29000")),
                new SeedProduct("FSH-005", "Fresh Tomatoes", "Fresh Food", "pack", "case", 12,
                        bd("16000"), bd("26000"))
        );

        int seq = 9000;
        for (SeedProduct sp : products) {
            seq++;
            String code = CODE_PREFIX + sp.code();
            Integer categoryId = categoryIds.get(sp.category());
            if (categoryId == null) {
                continue;
            }
            String barcode = String.format("8936%09d", seq);
            jdbcTemplate.update(
                    """
                            INSERT INTO products
                                (code, barcode, name, category_id, unit, import_unit, units_per_import_unit,
                                 reference_import_price, default_sale_price, scope, status)
                            SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, 'GLOBAL', 'active'
                            WHERE NOT EXISTS (SELECT 1 FROM products WHERE code = ?)
                            """,
                    code, barcode, sp.name(), categoryId, sp.baseUnit(), sp.caseUnit(), sp.caseQty(),
                    sp.importPrice(), sp.salePrice(), code);

            Integer productId = jdbcTemplate.queryForObject(
                    "SELECT id FROM products WHERE code = ?", Integer.class, code);
            if (productId == null) {
                continue;
            }
            insertPackagingIfMissing(productId, sp.baseUnit(), 1, barcode, true, 0, false);
            String caseBarcode = String.format("8935%09d", seq);
            insertPackagingIfMissing(productId, sp.caseUnit(), sp.caseQty(), caseBarcode, false, 1, true);
        }
    }

    private void insertPackagingIfMissing(int productId, String name, int conversionQty, String barcode,
            boolean isBase, int sortOrder, boolean isPurchaseDefault) {
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

    private void purgeWarehouseInventoryForShortDate() {
        jdbcTemplate.update("""
                DELETE wi FROM warehouse_inventory wi
                INNER JOIN products p ON p.id = wi.product_id
                INNER JOIN categories c ON c.id = p.category_id
                WHERE c.short_date = 1
                """);
        // Also purge any SD-* codes that may have been seeded into warehouse before the flag existed.
        jdbcTemplate.update("""
                DELETE wi FROM warehouse_inventory wi
                INNER JOIN products p ON p.id = wi.product_id
                WHERE p.code LIKE 'SD-%%'
                """);
    }

    private void ensureDemoSuppliers() {
        String[][] suppliers = {
                {"FreshCold Logistics", "Nguyen Van A", "0901000001", "active"},
                {"Arctic Foods Supply", "Tran Thi B", "0901000002", "active"},
                {"Daily Fresh Co.", "Le Van C", "0901000003", "active"},
        };
        for (String[] row : suppliers) {
            jdbcTemplate.update(
                    """
                            INSERT INTO suppliers (name, contact_person, phone, status)
                            SELECT ?, ?, ?, ?
                            WHERE NOT EXISTS (SELECT 1 FROM suppliers WHERE name = ?)
                            """,
                    row[0], row[1], row[2], row[3], row[0]);
        }
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private record SeedProduct(
            String code,
            String name,
            String category,
            String baseUnit,
            String caseUnit,
            int caseQty,
            BigDecimal importPrice,
            BigDecimal salePrice
    ) {}
}
