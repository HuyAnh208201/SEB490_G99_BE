package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures a realistic convenience-store style catalog with English retail/import units.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(2)
public class ProductCatalogSeedMigration {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogSeedMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            refreshCatalogMetadata();
            log.info("Product catalog seed metadata refreshed");
        } catch (Exception ex) {
            log.warn("Product catalog seed skipped: {}", ex.getMessage());
        }
    }

    private void refreshCatalogMetadata() {
        Object[][] products = {
                {"DRINK001", "893000000001", "Lavie Natural Mineral Water 500ml", "bottle", "case", 24},
                {"DRINK002", "893000000002", "Coca-Cola Can 320ml", "can", "case", 24},
                {"DRINK003", "893000000003", "Pepsi Can 320ml", "can", "case", 24},
                {"DRINK004", "893000000004", "Aquafina Drinking Water 500ml", "bottle", "case", 24},
                {"DRINK005", "893000000005", "Tiger Beer Can 330ml", "can", "case", 24},
                {"DRINK006", "893000000006", "Highlands Coffee Ready-to-Drink 180ml", "bottle", "case", 24},
                {"FOOD001", "893000000007", "Hao Hao Spicy Shrimp Instant Noodles", "pack", "carton", 30},
                {"FOOD002", "893000000008", "Banh Mi Thit Xien (Pork Sandwich)", "piece", "carton", 20},
                {"FOOD003", "893000000009", "Omachi Beef Pho Instant Noodles", "pack", "carton", 30},
                {"FOOD004", "893000000010", "Lay's Classic Potato Chips 56g", "pack", "carton", 24},
                {"MILK001", "893000000011", "Vinamilk Fresh Milk 180ml", "box", "carton", 12},
                {"MILK002", "893000000012", "TH True Yogurt Strawberry 100g", "cup", "case", 24},
                {"HOUSE001", "893000000013", "Lifebuoy Hand Wash 500ml", "bottle", "case", 12},
                {"HOUSE002", "893000000014", "Ariel Laundry Detergent Sachet 80g", "pack", "carton", 40},
                {"SNACK001", "893000000015", "Oreo Original Cookies 95g", "pack", "carton", 24},
                {"SNACK002", "893000000016", "KitKat 4-Finger Chocolate Bar", "pack", "carton", 24},
                {"CANDY001", "893000000017", "Mentos Mint Roll", "pack", "carton", 30},
        };

        for (Object[] row : products) {
            String code = String.valueOf(row[0]);
            String barcode = String.valueOf(row[1]);
            String unit = String.valueOf(row[3]);
            if ("cup".equals(unit)) {
                unit = "box";
            }
            // Never force a barcode that another SKU already owns (unique constraint).
            jdbcTemplate.update(
                    """
                            UPDATE products
                            SET name = ?,
                                unit = ?,
                                import_unit = ?,
                                units_per_import_unit = ?,
                                scope = 'GLOBAL',
                                branch_id = NULL
                            WHERE code = ?
                            """,
                    row[2], unit, row[4], row[5], code);
            Integer taken = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*) FROM products
                            WHERE barcode = ? AND code <> ?
                            """,
                    Integer.class,
                    barcode,
                    code);
            if (taken == null || taken == 0) {
                jdbcTemplate.update("UPDATE products SET barcode = ? WHERE code = ?", barcode, code);
            }
        }
    }
}
