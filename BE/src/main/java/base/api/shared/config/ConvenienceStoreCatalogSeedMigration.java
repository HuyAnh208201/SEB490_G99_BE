package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds a realistic Vietnamese convenience-store catalog (~90 SKUs) with English retail
 * units, multi-tier packaging (base -> optional mid -> case/carton) and unique 893...
 * EAN-13-style barcodes. Also seeds warehouse and sample branch inventory for the new SKUs.
 *
 * The {@code product_packagings} table is ensured by {@link ProductPackagingMigration}
 * (and created here if still missing). Everything is idempotent: every
 * insert is guarded by a NOT EXISTS check so re-running on an already-seeded database is a
 * no-op.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(4)
public class ConvenienceStoreCatalogSeedMigration {

    private static final Logger log = LoggerFactory.getLogger(ConvenienceStoreCatalogSeedMigration.class);

    /** Common prefix for every code created by this seed, used for idempotency checks. */
    private static final String CODE_PREFIX = "CVS-";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            ensureCategories();
            ensureExtraRetailUnits();
            ensurePackagingTable();

            Map<String, Integer> categoryIds = loadCategoryIds();
            List<SeedProduct> seedProducts = buildSeedProducts();
            seedProductsAndPackagings(seedProducts, categoryIds);
            seedWarehouseInventory();
            seedBranchInventory();

            log.info("Convenience store catalog seed ensured ({} SKUs)", seedProducts.size());
        } catch (Exception ex) {
            log.warn("Convenience store catalog seed skipped: {}", ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Categories
    // ------------------------------------------------------------------

    private static final String[] CATEGORIES = {
            "Drinks", "Instant food", "Dairy", "Snacks", "Personal care",
            "Household", "Tobacco & Beer", "Ice cream", "Bakery",
    };

    private void ensureCategories() {
        for (String name : CATEGORIES) {
            jdbcTemplate.update(
                    """
                            INSERT INTO categories (name, parent_id, description)
                            SELECT ?, NULL, ?
                            WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = ?)
                            """,
                    name, name + " products for convenience stores", name);
        }
    }

    private Map<String, Integer> loadCategoryIds() {
        Map<String, Integer> map = new HashMap<>();
        jdbcTemplate.query("SELECT id, name FROM categories", rs -> {
            map.put(rs.getString("name"), rs.getInt("id"));
        });
        return map;
    }

    // ------------------------------------------------------------------
    // Retail unit vocabulary (units_of_measure) — add any codes this seed needs
    // that ProductScopeAndUnitsMigration did not already provide.
    // ------------------------------------------------------------------

    private void ensureExtraRetailUnits() {
        String[][] extraRetail = {
                {"tube", "Tube"},
                {"bar", "Bar"},
                {"cup", "Cup"},
                {"tub", "Tub"},
                {"sleeve", "Sleeve (mid-pack)"},
                {"tray", "Tray (mid-pack)"},
                {"bowl", "Bowl"},
        };
        for (String[] row : extraRetail) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO units_of_measure (code, label_en, unit_type) VALUES (?, ?, 'RETAIL')",
                    row[0], row[1]);
        }
    }

    // ------------------------------------------------------------------
    // product_packagings table (no JPA entity yet)
    // ------------------------------------------------------------------

    private void ensurePackagingTable() {
        jdbcTemplate.execute(
                """
                        CREATE TABLE IF NOT EXISTS product_packagings (
                            id INT NOT NULL AUTO_INCREMENT,
                            product_id INT NOT NULL,
                            name VARCHAR(255) NOT NULL,
                            conversion_qty INT NOT NULL,
                            barcode VARCHAR(255) NULL,
                            is_base TINYINT(1) NOT NULL DEFAULT 0,
                            PRIMARY KEY (id),
                            KEY idx_product_packagings_product (product_id)
                        )
                        """);
        addColumnIfMissing("product_packagings", "sort_order", "INT NOT NULL DEFAULT 0");
        addColumnIfMissing("product_packagings", "is_purchase_default", "TINYINT(1) NOT NULL DEFAULT 0");
    }

    // ------------------------------------------------------------------
    // Products + packagings
    // ------------------------------------------------------------------

    private void seedProductsAndPackagings(List<SeedProduct> products, Map<String, Integer> categoryIds) {
        int baseSeq = 0;
        int midSeq = 0;
        int caseSeq = 0;

        for (SeedProduct sp : products) {
            baseSeq++;
            String code = CODE_PREFIX + sp.code();
            String baseBarcode = String.format("8939%09d", baseSeq);
            Integer categoryId = categoryIds.get(sp.category());

            jdbcTemplate.update(
                    """
                            INSERT INTO products
                                (code, barcode, name, category_id, unit, import_unit, units_per_import_unit,
                                 reference_import_price, default_sale_price, scope, status)
                            SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, 'GLOBAL', 'active'
                            WHERE NOT EXISTS (SELECT 1 FROM products WHERE code = ?)
                            """,
                    code, baseBarcode, sp.name(), categoryId, sp.baseUnit(), sp.caseUnit(), sp.caseQty(),
                    sp.importPrice(), sp.salePrice(), code);

            Integer productId = jdbcTemplate.queryForObject(
                    "SELECT id FROM products WHERE code = ?", Integer.class, code);
            if (productId == null) {
                continue;
            }

            int sortOrder = 0;
            insertPackagingIfMissing(productId, sp.baseUnit(), 1, baseBarcode, true, sortOrder++, false);

            if (sp.midUnit() != null) {
                midSeq++;
                String midBarcode = String.format("8938%09d", midSeq);
                insertPackagingIfMissing(productId, sp.midUnit(), sp.midQty(), midBarcode, false, sortOrder++, false);
            }

            caseSeq++;
            String caseBarcode = String.format("8937%09d", caseSeq);
            insertPackagingIfMissing(productId, sp.caseUnit(), sp.caseQty(), caseBarcode, false, sortOrder, true);
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

    // ------------------------------------------------------------------
    // Warehouse + branch inventory for the new SKUs
    // ------------------------------------------------------------------

    private void seedWarehouseInventory() {
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

        // Every ~11th SKU is seeded lean (right at one case) so it shows up as a
        // "needs restock" candidate; the rest get a comfortable buffer.
        jdbcTemplate.update(
                """
                        INSERT INTO warehouse_inventory (product_id, quantity, reorder_point, updated_at)
                        SELECT p.id,
                               CASE WHEN (p.id %% 11) = 0
                                    THEN p.units_per_import_unit
                                    ELSE p.units_per_import_unit * 8
                               END AS quantity,
                               p.units_per_import_unit * 2 AS reorder_point,
                               NOW() AS updated_at
                        FROM products p
                        WHERE p.code LIKE '%s%%'
                          AND NOT EXISTS (SELECT 1 FROM warehouse_inventory w WHERE w.product_id = p.id)
                        """.formatted(CODE_PREFIX));
    }

    private void seedBranchInventory() {
        ensureBranchInventoryUniqueKey();

        jdbcTemplate.update(
                """
                        INSERT INTO branch_inventory (branch_id, product_id, quantity, reorder_point, updated_at)
                        SELECT b.id,
                               p.id,
                               CASE WHEN (p.id %% 9) = 0 THEN 3 ELSE 15 + (p.id %% 20) END AS quantity,
                               10 AS reorder_point,
                               NOW() AS updated_at
                        FROM branches b
                        JOIN products p ON p.code LIKE '%s%%'
                        WHERE NOT EXISTS (
                            SELECT 1 FROM branch_inventory bi
                            WHERE bi.branch_id = b.id AND bi.product_id = p.id
                        )
                        """.formatted(CODE_PREFIX));
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

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void addColumnIfMissing(String table, String column, String definition) {
        Integer exists = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                          AND COLUMN_NAME = ?
                        """,
                Integer.class, table, column);
        if (exists == null || exists == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    // ------------------------------------------------------------------
    // Seed data
    // ------------------------------------------------------------------

    private record SeedProduct(
            String code,
            String name,
            String category,
            String baseUnit,
            String midUnit,
            int midQty,
            String caseUnit,
            int caseQty,
            BigDecimal importPrice,
            BigDecimal salePrice) {
    }

    private static SeedProduct p(String code, String name, String category, String baseUnit,
            String caseUnit, int caseQty, long importPrice, long salePrice) {
        return new SeedProduct(code, name, category, baseUnit, null, 0, caseUnit, caseQty,
                BigDecimal.valueOf(importPrice), BigDecimal.valueOf(salePrice));
    }

    private static SeedProduct pm(String code, String name, String category, String baseUnit,
            String midUnit, int midQty, String caseUnit, int caseQty, long importPrice, long salePrice) {
        return new SeedProduct(code, name, category, baseUnit, midUnit, midQty, caseUnit, caseQty,
                BigDecimal.valueOf(importPrice), BigDecimal.valueOf(salePrice));
    }

    private List<SeedProduct> buildSeedProducts() {
        return List.of(
                // ---------------- Drinks (16) ----------------
                p("DRK-001", "Lavie Natural Mineral Water 1.5L", "Drinks", "bottle", "case", 12, 6500, 11000),
                p("DRK-002", "Aquafina Purified Drinking Water 350ml", "Drinks", "bottle", "case", 24, 3000, 5000),
                p("DRK-003", "Coca-Cola Bottle 390ml", "Drinks", "bottle", "case", 24, 6000, 10000),
                p("DRK-004", "Pepsi Cola Bottle 390ml", "Drinks", "bottle", "case", 24, 6000, 10000),
                pm("DRK-005", "7Up Lemon Soda Can 330ml", "Drinks", "can", "sleeve", 6, "case", 24, 6500, 11000),
                pm("DRK-006", "Sprite Lemon Lime Can 330ml", "Drinks", "can", "sleeve", 6, "case", 24, 6500, 11000),
                p("DRK-007", "Fanta Orange Can 330ml", "Drinks", "can", "case", 24, 6500, 11000),
                p("DRK-008", "Number 1 Energy Drink 330ml", "Drinks", "can", "case", 24, 6000, 10000),
                p("DRK-009", "Red Bull Energy Drink 250ml", "Drinks", "can", "case", 24, 10000, 16000),
                p("DRK-010", "C2 Green Tea Peach 455ml", "Drinks", "bottle", "case", 24, 7000, 12000),
                p("DRK-011", "Not Green Tea Original 455ml", "Drinks", "bottle", "case", 24, 7000, 12000),
                p("DRK-012", "Nescafe Ice Coffee 235ml", "Drinks", "can", "case", 24, 8000, 13000),
                p("DRK-013", "Twister Orange Juice 455ml", "Drinks", "bottle", "case", 24, 8500, 14000),
                p("DRK-014", "Wonderfarm Chrysanthemum Tea Can 310ml", "Drinks", "can", "case", 24, 6000, 10000),
                p("DRK-015", "Sting Gold Energy Drink 330ml", "Drinks", "can", "case", 24, 6500, 11000),
                p("DRK-016", "Fuze Tea Lemon 450ml", "Drinks", "bottle", "case", 24, 7500, 12500),

                // ---------------- Instant food (12) ----------------
                p("INF-001", "Hao Hao Instant Noodles Sour Shrimp 75g", "Instant food", "pack", "carton", 30, 3200, 5500),
                p("INF-002", "Omachi Spicy Beef Instant Noodles 80g", "Instant food", "pack", "carton", 30, 6000, 9500),
                p("INF-003", "Kokomi Instant Noodles Sate Onion 90g", "Instant food", "pack", "carton", 30, 3800, 6000),
                p("INF-004", "Cung Dinh Instant Rice Porridge Chicken 50g", "Instant food", "pack", "carton", 24, 4500, 7500),
                p("INF-005", "Vifon Instant Pho Bo 65g", "Instant food", "bowl", "carton", 24, 8000, 13000),
                p("INF-006", "Vifon Instant Hu Tieu Nam Vang 65g", "Instant food", "bowl", "carton", 24, 8000, 13000),
                p("INF-007", "Miliket Instant Noodles Squid Flavor 75g", "Instant food", "pack", "carton", 30, 2800, 4500),
                p("INF-008", "3 Mien Instant Noodles Chua Cay 65g", "Instant food", "pack", "carton", 30, 3200, 5000),
                p("INF-009", "Number 1 Instant Vermicelli Bo Kho 70g", "Instant food", "bowl", "carton", 24, 7500, 12000),
                p("INF-010", "Ha Long Canned Braised Pork 170g", "Instant food", "can", "carton", 24, 22000, 32000),
                p("INF-011", "Tuna Chunks Canned in Oil 185g", "Instant food", "can", "carton", 24, 18000, 27000),
                p("INF-012", "Golden Chicken Instant Congee Cup 40g", "Instant food", "cup", "carton", 24, 6500, 10500),

                // ---------------- Dairy (10) ----------------
                p("DAI-001", "Vinamilk Fresh Milk Unsweetened 1L", "Dairy", "box", "carton", 12, 28000, 37000),
                p("DAI-002", "TH True Milk Fresh Milk 180ml", "Dairy", "box", "carton", 24, 6500, 10000),
                pm("DAI-003", "Vinamilk Yogurt Drink 170ml", "Dairy", "bottle", "tray", 4, "carton", 24, 5000, 8500),
                p("DAI-004", "Vinamilk Probi Yogurt Drink 65ml", "Dairy", "bottle", "carton", 24, 3500, 6000),
                pm("DAI-005", "TH True Yogurt Cup Strawberry 100g", "Dairy", "cup", "tray", 4, "carton", 24, 5500, 9000),
                p("DAI-006", "Ong Tho Condensed Milk 380g", "Dairy", "can", "carton", 24, 18000, 27000),
                p("DAI-007", "Longevity Cow Condensed Milk 380g", "Dairy", "can", "carton", 24, 17500, 26000),
                p("DAI-008", "Vinasoy Fami Soy Milk 200ml", "Dairy", "box", "carton", 24, 5500, 9000),
                p("DAI-009", "Moc Chau Cheese Slice 130g", "Dairy", "box", "carton", 24, 25000, 36000),
                p("DAI-010", "Vinamilk Whipping Cream 150ml", "Dairy", "box", "carton", 24, 15000, 22000),

                // ---------------- Snacks (16) ----------------
                p("SNK-001", "Oreo Original Cookies 137g", "Snacks", "pack", "carton", 24, 12000, 19000),
                p("SNK-002", "Cosy Marie Biscuits 200g", "Snacks", "pack", "carton", 24, 10000, 16000),
                p("SNK-003", "Custas Cream Cake 6pcs", "Snacks", "pack", "carton", 24, 14000, 22000),
                p("SNK-004", "Chocopie Orion 12pcs", "Snacks", "pack", "carton", 16, 32000, 46000),
                p("SNK-005", "Lay's Classic Potato Chips 54g", "Snacks", "pack", "carton", 24, 9000, 14000),
                p("SNK-006", "Oishi Prawn Crackers 40g", "Snacks", "pack", "carton", 40, 5000, 8000),
                p("SNK-007", "Poca Potato Chips BBQ 40g", "Snacks", "pack", "carton", 40, 5500, 9000),
                p("SNK-008", "KitKat 4-Finger Chocolate 41.5g", "Snacks", "pack", "carton", 24, 10000, 16000),
                p("SNK-009", "Alpenliebe Candy Bag 76g", "Snacks", "bag", "carton", 24, 8000, 13000),
                p("SNK-010", "Mentos Mint Roll 37g", "Snacks", "roll", "carton", 40, 6000, 10000),
                p("SNK-011", "Milo Chocolate Bar 36g", "Snacks", "bar", "carton", 24, 6500, 10500),
                p("SNK-012", "Toblerone Chocolate Bar 100g", "Snacks", "bar", "carton", 20, 45000, 65000),
                p("SNK-013", "Roasted Cashew Nuts 100g", "Snacks", "pack", "carton", 24, 22000, 32000),
                p("SNK-014", "Dried Mango Slices 100g", "Snacks", "pack", "carton", 24, 18000, 27000),
                p("SNK-015", "Haihaco Shrimp Chips 30g", "Snacks", "pack", "carton", 40, 3500, 6000),
                p("SNK-016", "Danisa Butter Cookies 200g", "Snacks", "box", "carton", 12, 45000, 65000),

                // ---------------- Personal care (10) ----------------
                p("PCR-001", "Colgate Toothpaste Total 12 170g", "Personal care", "tube", "carton", 24, 24000, 35000),
                p("PCR-002", "P/S Toothpaste Salt Herbal 180g", "Personal care", "tube", "carton", 24, 18000, 27000),
                p("PCR-003", "Clear Shampoo Cool Sport 170ml", "Personal care", "bottle", "carton", 24, 32000, 46000),
                p("PCR-004", "Sunsilk Shampoo Smooth & Manageable 170ml", "Personal care", "bottle", "carton", 24, 30000, 44000),
                pm("PCR-005", "Lifebuoy Antibacterial Soap Bar 90g", "Personal care", "bar", "sleeve", 6, "carton", 48, 6500, 10000),
                pm("PCR-006", "Dove Beauty Soap Bar 90g", "Personal care", "bar", "sleeve", 6, "carton", 48, 7500, 11500),
                p("PCR-007", "Pulppy Facial Tissue Box 200 Sheets", "Personal care", "box", "carton", 24, 12000, 18000),
                p("PCR-008", "Bamboo Toilet Paper Roll 10-Pack", "Personal care", "pack", "carton", 6, 45000, 62000),
                p("PCR-009", "Antibacterial Wet Wipes 80 Sheets", "Personal care", "pack", "carton", 24, 15000, 23000),
                p("PCR-010", "Diana Sanitary Pads Night Wing 8pcs", "Personal care", "pack", "carton", 24, 16000, 24000),

                // ---------------- Household (10) ----------------
                p("HHD-001", "Omo Laundry Detergent Powder 800g", "Household", "bag", "carton", 12, 42000, 58000),
                p("HHD-002", "Ariel Laundry Detergent Liquid 1.5L", "Household", "bottle", "carton", 6, 95000, 130000),
                p("HHD-003", "Sunlight Dishwashing Liquid 750g", "Household", "bottle", "carton", 12, 22000, 32000),
                p("HHD-004", "Comfort Fabric Softener 800ml", "Household", "bottle", "carton", 12, 38000, 52000),
                p("HHD-005", "Duy Tan Garbage Bag Roll 10pcs", "Household", "roll", "carton", 24, 12000, 18000),
                p("HHD-006", "Panasonic Alkaline Battery AA 4-Pack", "Household", "pack", "carton", 24, 28000, 40000),
                p("HHD-007", "Rid Insect Killer Spray 300ml", "Household", "can", "carton", 12, 45000, 62000),
                p("HHD-008", "Glade Air Freshener Spray 320ml", "Household", "can", "carton", 12, 42000, 58000),
                p("HHD-009", "Thuan Phat Matches Box 10-Pack", "Household", "box", "carton", 20, 5000, 8000),
                p("HHD-010", "Refillable Gas Lighter", "Household", "piece", "carton", 50, 4000, 7000),

                // ---------------- Tobacco & Beer (8) ----------------
                pm("TBB-001", "Saigon Special Beer Can 330ml", "Tobacco & Beer", "can", "sleeve", 6, "case", 24, 9000, 14000),
                p("TBB-002", "333 Premium Beer Can 330ml", "Tobacco & Beer", "can", "case", 24, 9500, 15000),
                pm("TBB-003", "Tiger Crystal Beer Can 330ml", "Tobacco & Beer", "can", "sleeve", 6, "case", 24, 11000, 17000),
                pm("TBB-004", "Heineken Beer Bottle 330ml", "Tobacco & Beer", "bottle", "sleeve", 6, "case", 24, 13000, 20000),
                p("TBB-005", "Hanoi Beer Bottle 450ml", "Tobacco & Beer", "bottle", "case", 20, 8500, 13500),
                p("TBB-006", "Strongbow Apple Cider Can 330ml", "Tobacco & Beer", "can", "case", 24, 15000, 22000),
                p("TBB-007", "Vinataba Cigarettes Pack 20s", "Tobacco & Beer", "pack", "carton", 10, 16000, 20000),
                p("TBB-008", "Thang Long Cigarettes Pack 20s", "Tobacco & Beer", "pack", "carton", 10, 14000, 18000),

                // ---------------- Ice cream (6) ----------------
                p("ICE-001", "Wall's Cornetto Chocolate Cone", "Ice cream", "piece", "carton", 24, 9000, 15000),
                pm("ICE-002", "Merino Vanilla Cup 60ml", "Ice cream", "cup", "tray", 6, "carton", 24, 4000, 7000),
                p("ICE-003", "Trang Tien Ice Cream Stick Bar", "Ice cream", "piece", "carton", 30, 6000, 10000),
                p("ICE-004", "Paddle Pop Rainbow Stick", "Ice cream", "piece", "carton", 24, 7000, 12000),
                p("ICE-005", "Wall's Vanilla Ice Cream Tub 450ml", "Ice cream", "tub", "carton", 12, 32000, 48000),
                p("ICE-006", "Celano Mochi Ice Cream Box 6pcs", "Ice cream", "box", "carton", 12, 45000, 65000),

                // ---------------- Bakery (8) ----------------
                p("BAK-001", "Banh Mi Thit Nguoi Sandwich", "Bakery", "piece", "carton", 20, 9000, 15000),
                p("BAK-002", "Butter Croissant", "Bakery", "piece", "carton", 24, 8000, 14000),
                p("BAK-003", "Kinh Do Sponge Cake Cup", "Bakery", "piece", "carton", 24, 4500, 8000),
                p("BAK-004", "Danish Pastry Cheese", "Bakery", "piece", "carton", 20, 10000, 17000),
                p("BAK-005", "Custard Bun with Chicken Floss", "Bakery", "piece", "carton", 20, 9500, 16000),
                p("BAK-006", "Chewy Butter Cookies 5pcs", "Bakery", "pack", "carton", 24, 12000, 19000),
                p("BAK-007", "Steamed Pork Bun (Banh Bao)", "Bakery", "piece", "carton", 20, 8000, 14000),
                p("BAK-008", "Chocolate Glazed Donut", "Bakery", "piece", "carton", 24, 9000, 15000));
    }
}
