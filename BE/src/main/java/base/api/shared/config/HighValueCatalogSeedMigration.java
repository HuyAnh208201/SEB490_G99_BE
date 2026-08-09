package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Seeds high-value / high-theft-risk SKUs used by Shift Closing verification.
 * Idempotent — safe on every BE startup.
 */
@Component
@Order(5)
public class HighValueCatalogSeedMigration {

    private static final Logger log = LoggerFactory.getLogger(HighValueCatalogSeedMigration.class);
    private static final String CODE_PREFIX = "CVS-HV-";

    private static final String[] CATEGORIES = {
            "High-value tobacco",
            "Cosmetics & beauty",
            "Prepaid & service cards",
            "Premium alcohol",
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            Integer manualCatalog = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM products WHERE code = 'TOB001'",
                    Integer.class);
            if (manualCatalog != null && manualCatalog > 0) {
                log.info("High-value SQL catalog (TOB001…) detected — skipping CVS-HV seed");
                return;
            }
            for (String name : CATEGORIES) {
                jdbcTemplate.update(
                        """
                                INSERT INTO categories (name, parent_id, description)
                                SELECT ?, NULL, ?
                                WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = ?)
                                """,
                        name, "Shift closing high-value verification", name);
            }
            seedProducts();
            seedBranchInventory();
            log.info("High-value catalog seed ensured");
        } catch (Exception ex) {
            log.warn("High-value catalog seed skipped: {}", ex.getMessage());
        }
    }

    private void seedProducts() {
        Object[][] rows = {
                // code, name, category, unit, import, sale
                {"001", "Marlboro Red Carton 10 packs", "High-value tobacco", "carton", 480000, 550000},
                {"002", "555 State Express Carton 10 packs", "High-value tobacco", "carton", 450000, 520000},
                {"003", "Davidoff Mini Cigarillo Tin 20pcs", "High-value tobacco", "box", 580000, 680000},
                {"004", "Dunhill Swiss Blend Premium Pack", "High-value tobacco", "pack", 260000, 320000},
                {"005", "MAC Lipstick Ruby Woo 3g", "Cosmetics & beauty", "piece", 520000, 650000},
                {"006", "La Roche-Posay Anthelios SPF50+ 50ml", "Cosmetics & beauty", "tube", 420000, 550000},
                {"007", "Estée Lauder Advanced Night Repair 30ml", "Cosmetics & beauty", "bottle", 1500000, 1850000},
                {"008", "YSL Rouge Volupté Shine Lipstick", "Cosmetics & beauty", "piece", 580000, 720000},
                {"009", "Viettel Prepaid Card 500,000 VND", "Prepaid & service cards", "piece", 495000, 500000},
                {"010", "Vinaphone Prepaid Card 500,000 VND", "Prepaid & service cards", "piece", 495000, 500000},
                {"011", "Garena Prepaid Card 500,000 VND", "Prepaid & service cards", "piece", 490000, 500000},
                {"012", "Steam Wallet Card 500,000 VND", "Prepaid & service cards", "piece", 490000, 500000},
                {"013", "Johnnie Walker Red Label 750ml", "Premium alcohol", "bottle", 520000, 650000},
                {"014", "Hennessy VS Cognac 700ml", "Premium alcohol", "bottle", 980000, 1200000},
                {"015", "Corona Extra Import 6-Pack", "Premium alcohol", "pack", 420000, 520000},
                {"016", "Suntory Kakubin Whisky 700ml", "Premium alcohol", "bottle", 460000, 580000},
        };

        for (Object[] row : rows) {
            String code = CODE_PREFIX + row[0];
            String barcode = "8932000" + row[0];
            jdbcTemplate.update(
                    """
                            INSERT INTO products
                                (code, barcode, name, description, category_id, unit,
                                 reference_import_price, default_sale_price, status)
                            SELECT ?, ?, ?, ?, c.id, ?, ?, ?, 'active'
                            FROM categories c
                            WHERE c.name = ?
                              AND NOT EXISTS (SELECT 1 FROM products p WHERE p.code = ?)
                            """,
                    code,
                    barcode,
                    row[1],
                    row[1],
                    row[3],
                    row[4],
                    row[5],
                    row[2],
                    code);
        }
    }

    private void seedBranchInventory() {
        jdbcTemplate.update(
                """
                        INSERT INTO branch_inventory (branch_id, product_id, quantity, reorder_point, updated_at)
                        SELECT b.id, p.id,
                               8 + (p.id %% 12) AS quantity,
                               4 AS reorder_point,
                               NOW()
                        FROM branches b
                        JOIN products p ON p.code LIKE '%s%%'
                        WHERE NOT EXISTS (
                            SELECT 1 FROM branch_inventory bi
                            WHERE bi.branch_id = b.id AND bi.product_id = p.id
                        )
                        """.formatted(CODE_PREFIX));
    }
}
