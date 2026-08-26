package base.api.tools;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One-off catalog hard-delete: reduce active products to a target size, balanced per category.
 * Not wired to application startup — run via {@link CatalogProductTrimRunner} against remote DB.
 */
public final class CatalogProductTrimmer {

    private static final int BATCH_SIZE = 200;

    private final JdbcTemplate jdbc;
    private final int targetSize;

    public CatalogProductTrimmer(JdbcTemplate jdbc, int targetSize) {
        this.jdbc = jdbc;
        this.targetSize = Math.max(1, targetSize);
    }

    public static JdbcTemplate jdbcFromUrl(String url, String username, String password) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        return new JdbcTemplate(ds);
    }

    public TrimResult run() {
        if (!tableExists("products")) {
            throw new IllegalStateException("products table missing");
        }
        int before = countActiveProducts();
        if (before <= targetSize) {
            return new TrimResult(before, before, 0, List.of());
        }

        Set<Integer> keepIds = selectKeepers();
        Set<Integer> deleteIds = loadActiveProductIds().stream()
                .filter(id -> !keepIds.contains(id))
                .collect(Collectors.toCollection(HashSet::new));
        if (deleteIds.isEmpty()) {
            return new TrimResult(before, before, 0, List.of());
        }

        List<String> categoryLines = buildCategorySummary(keepIds);
        purgeChildRows(deleteIds);
        int deleted = deleteProducts(deleteIds);
        int after = countActiveProducts();
        return new TrimResult(before, after, deleted, categoryLines);
    }

    private Set<Integer> selectKeepers() {
        List<ProductRow> active = loadActiveProducts();
        Set<Integer> keep = active.stream()
                .filter(ProductRow::pinned)
                .map(ProductRow::id)
                .collect(Collectors.toCollection(HashSet::new));

        int remaining = targetSize - keep.size();
        if (remaining <= 0) {
            return keep;
        }

        Map<Integer, List<ProductRow>> byCategory = active.stream()
                .filter(p -> !p.pinned())
                .collect(Collectors.groupingBy(ProductRow::categoryId));
        if (byCategory.isEmpty()) {
            return keep;
        }

        List<CategoryQuota> categories = byCategory.entrySet().stream()
                .map(e -> new CategoryQuota(e.getKey(), e.getValue().size()))
                .sorted(Comparator
                        .comparingInt(CategoryQuota::unpinnedCount).reversed()
                        .thenComparingInt(CategoryQuota::categoryId))
                .toList();

        Map<Integer, Integer> quotaByCategory = allocateQuotas(categories, remaining);
        for (Map.Entry<Integer, List<ProductRow>> entry : byCategory.entrySet()) {
            int quota = quotaByCategory.getOrDefault(entry.getKey(), 0);
            if (quota <= 0) {
                continue;
            }
            entry.getValue().stream()
                    .sorted(Comparator
                            .comparingInt(ProductRow::hasInventory).reversed()
                            .thenComparingInt(ProductRow::id))
                    .limit(quota)
                    .forEach(row -> keep.add(row.id()));
        }
        return keep;
    }

    private Map<Integer, Integer> allocateQuotas(List<CategoryQuota> categories, int remaining) {
        Map<Integer, Integer> quotas = new LinkedHashMap<>();
        int base = remaining / categories.size();
        int assigned = 0;
        for (CategoryQuota cat : categories) {
            int q = Math.min(base, cat.unpinnedCount());
            quotas.put(cat.categoryId(), q);
            assigned += q;
        }
        int toDistribute = remaining - assigned;
        while (toDistribute > 0) {
            boolean progressed = false;
            for (CategoryQuota cat : categories) {
                int current = quotas.getOrDefault(cat.categoryId(), 0);
                if (current >= cat.unpinnedCount()) {
                    continue;
                }
                quotas.put(cat.categoryId(), current + 1);
                toDistribute--;
                progressed = true;
                if (toDistribute <= 0) {
                    break;
                }
            }
            if (!progressed) {
                break;
            }
        }
        return quotas;
    }

    private List<String> buildCategorySummary(Set<Integer> keepIds) {
        List<Map<String, Object>> cats = jdbc.queryForList("""
                SELECT c.id, c.name, COUNT(p.id) AS total
                FROM categories c
                LEFT JOIN products p ON p.category_id = c.id AND p.status = 'active'
                GROUP BY c.id, c.name
                HAVING total > 0
                ORDER BY c.name
                """);
        Map<Integer, Integer> keptByCategory = new HashMap<>();
        for (ProductRow row : loadActiveProducts()) {
            if (keepIds.contains(row.id())) {
                keptByCategory.merge(row.categoryId(), 1, Integer::sum);
            }
        }
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> cat : cats) {
            int catId = ((Number) cat.get("id")).intValue();
            int total = ((Number) cat.get("total")).intValue();
            int kept = keptByCategory.getOrDefault(catId, 0);
            lines.add(String.format("  %s (id=%d): keep %d/%d", cat.get("name"), catId, kept, total));
        }
        return lines;
    }

    private void purgeChildRows(Set<Integer> productIds) {
        List<Integer> ids = new ArrayList<>(productIds);
        if (tableExists("pos_scan_events")) {
            applyChunks(ids, "UPDATE pos_scan_events SET product_id = NULL WHERE product_id IN (%s)");
        }
        applyChunks(ids, "DELETE FROM inventory_count_items WHERE product_id IN (%s)");
        if (tableExists("shift_session_high_value_items")) {
            applyChunks(ids, "DELETE FROM shift_session_high_value_items WHERE product_id IN (%s)");
        }
        applyChunks(ids, "DELETE FROM order_items WHERE product_id IN (%s)");
        applyChunks(ids, "DELETE FROM purchase_request_items WHERE product_id IN (%s)");
        applyChunks(ids, "DELETE FROM purchase_order_items WHERE product_id IN (%s)");
        applyChunks(ids, "DELETE FROM goods_receipt_items WHERE product_id IN (%s)");
        applyChunks(ids, "DELETE FROM product_sale_prices WHERE product_id IN (%s)");
        applyChunks(ids, "DELETE FROM product_packagings WHERE product_id IN (%s)");
        applyChunks(ids, "DELETE FROM branch_inventory WHERE product_id IN (%s)");
        applyChunks(ids, "DELETE FROM warehouse_inventory WHERE product_id IN (%s)");
    }

    private int deleteProducts(Set<Integer> productIds) {
        return applyChunks(new ArrayList<>(productIds), "DELETE FROM products WHERE id IN (%s)");
    }

    private int applyChunks(List<Integer> ids, String sqlTemplate) {
        int total = 0;
        for (int i = 0; i < ids.size(); i += BATCH_SIZE) {
            List<Integer> chunk = ids.subList(i, Math.min(i + BATCH_SIZE, ids.size()));
            total += jdbc.update(sqlTemplate.formatted(placeholders(chunk)), chunk.toArray());
        }
        return total;
    }

    private List<ProductRow> loadActiveProducts() {
        return jdbc.query("""
                SELECT p.id, p.category_id, p.code,
                       CASE WHEN bi.product_id IS NOT NULL THEN 1 ELSE 0 END AS has_inventory
                FROM products p
                LEFT JOIN (SELECT DISTINCT product_id FROM branch_inventory) bi ON bi.product_id = p.id
                WHERE p.status = 'active'
                ORDER BY p.category_id, p.id
                """, (rs, rowNum) -> {
            String code = rs.getString("code");
            boolean pinned = code != null && (code.startsWith("SD-") || code.startsWith("CVS-HV-"));
            return new ProductRow(
                    rs.getInt("id"), rs.getInt("category_id"), code, pinned, rs.getInt("has_inventory"));
        });
    }

    private Set<Integer> loadActiveProductIds() {
        return loadActiveProducts().stream().map(ProductRow::id).collect(Collectors.toCollection(HashSet::new));
    }

    private int countActiveProducts() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE status = 'active'", Integer.class);
        return count == null ? 0 : count;
    }

    private boolean tableExists(String table) {
        Integer n = jdbc.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.TABLES
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                        """,
                Integer.class, table);
        return n != null && n > 0;
    }

    private static String placeholders(List<Integer> ids) {
        return ids.stream().map(id -> "?").collect(Collectors.joining(","));
    }

    private record ProductRow(int id, int categoryId, String code, boolean pinned, int hasInventory) {
    }

    private record CategoryQuota(int categoryId, int unpinnedCount) {
    }

    public record TrimResult(int before, int after, int deleted, List<String> categoryLines) {
    }
}
