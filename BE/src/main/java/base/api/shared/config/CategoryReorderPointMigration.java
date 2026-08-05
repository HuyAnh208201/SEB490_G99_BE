package base.api.shared.config;

import base.api.shared.util.CategoryReorderPoints;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Recalculates warehouse vs branch reorder_point from product category
 * (warehouse buffers larger than branch shelf buffers).
 */
@Component
@ConditionalOnStartupBootstrap
@Order(7)
public class CategoryReorderPointMigration {

    private static final Logger log = LoggerFactory.getLogger(CategoryReorderPointMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                            SELECT p.id AS product_id,
                                   p.units_per_import_unit AS pack_size,
                                   c.name AS category_name
                            FROM products p
                            LEFT JOIN categories c ON c.id = p.category_id
                            """);
            int warehouseUpdated = 0;
            int branchUpdated = 0;
            boolean branchHasReorder = columnExists("branch_inventory", "reorder_point");

            for (Map<String, Object> row : rows) {
                Integer productId = ((Number) row.get("product_id")).intValue();
                Integer pack = row.get("pack_size") == null ? null : ((Number) row.get("pack_size")).intValue();
                String category = row.get("category_name") == null ? null : String.valueOf(row.get("category_name"));
                int warehouseReorder = CategoryReorderPoints.forWarehouse(category, pack);
                int branchReorder = CategoryReorderPoints.forBranch(category, pack);

                warehouseUpdated += jdbcTemplate.update(
                        "UPDATE warehouse_inventory SET reorder_point = ? WHERE product_id = ?",
                        warehouseReorder, productId);
                if (branchHasReorder) {
                    branchUpdated += jdbcTemplate.update(
                            "UPDATE branch_inventory SET reorder_point = ? WHERE product_id = ?",
                            branchReorder, productId);
                }
            }
            log.info(
                    "Category reorder points applied (warehouse rows={}, branch rows={})",
                    warehouseUpdated, branchUpdated);
        } catch (Exception ex) {
            log.warn("Category reorder point migration skipped: {}", ex.getMessage());
        }
    }

    private boolean columnExists(String table, String column) {
        Integer n = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                          AND COLUMN_NAME = ?
                        """,
                Integer.class, table, column);
        return n != null && n > 0;
    }
}
