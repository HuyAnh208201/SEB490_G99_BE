package base.api.shared.config;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Collapses leftover Vietnamese / duplicate categories into the canonical
 * 12 English CVS categories and remaps products (including JSON ground truth).
 */
@Component
@ConditionalOnStartupBootstrap
@Order(6)
public class CategoryTaxonomyCleanupMigration {

    private static final Logger log = LoggerFactory.getLogger(CategoryTaxonomyCleanupMigration.class);

    private static final String[] CANONICAL = {
            "Drinks",
            "Instant food",
            "Snacks",
            "Dairy",
            "Bakery",
            "Ice cream",
            "Tobacco & Beer",
            "Alcohol",
            "Personal care",
            "Household",
            "Cosmetics",
            "Top-up / Digital"
    };

    private static final Map<String, String> NAME_MERGE = Map.ofEntries(
            Map.entry("đồ uống", "Drinks"),
            Map.entry("do uong", "Drinks"),
            Map.entry("thực phẩm nhanh", "Instant food"),
            Map.entry("thuc pham nhanh", "Instant food"),
            Map.entry("sữa & sản phẩm từ sữa", "Dairy"),
            Map.entry("sua & san pham tu sua", "Dairy"),
            Map.entry("gia dụng tiện ích", "Household"),
            Map.entry("gia dung tien ich", "Household"),
            Map.entry("thuốc lá", "Tobacco & Beer"),
            Map.entry("thuoc la", "Tobacco & Beer"),
            Map.entry("mỹ phẩm & dược mỹ phẩm", "Cosmetics"),
            Map.entry("my pham & duoc my pham", "Cosmetics"),
            Map.entry("thẻ cào & thẻ dịch vụ", "Top-up / Digital"),
            Map.entry("the cao & the dich vu", "Top-up / Digital"),
            Map.entry("đồ uống có cồn giá trị cao", "Alcohol"),
            Map.entry("do uong co con gia tri cao", "Alcohol")
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void migrate() {
        try {
            if (!tableExists("categories") || !tableExists("products")) {
                return;
            }
            ensureCanonicalCategories();
            Map<String, Integer> canonicalIds = loadCanonicalIds();
            int remappedByName = remapByLegacyCategoryName(canonicalIds);
            int remappedByJson = remapByCatalogJson(canonicalIds);
            int deleted = deleteEmptyNonCanonical();
            log.info(
                    "Category taxonomy cleanup: remappedByName={}, remappedByJson={}, deletedEmpty={}",
                    remappedByName, remappedByJson, deleted);
        } catch (Exception ex) {
            log.warn("Category taxonomy cleanup skipped: {}", ex.getMessage());
        }
    }

    private void ensureCanonicalCategories() {
        for (String name : CANONICAL) {
            Integer existing = jdbcTemplate.query(
                    "SELECT id FROM categories WHERE LOWER(TRIM(name)) = LOWER(?) LIMIT 1",
                    rs -> rs.next() ? rs.getInt(1) : null,
                    name);
            if (existing == null) {
                jdbcTemplate.update(
                        "INSERT INTO categories (name, description) VALUES (?, ?)",
                        name,
                        "ChainStore CVS category");
            } else {
                jdbcTemplate.update("UPDATE categories SET name = ? WHERE id = ?", name, existing);
            }
        }
    }

    private Map<String, Integer> loadCanonicalIds() {
        Map<String, Integer> ids = new LinkedHashMap<>();
        for (String name : CANONICAL) {
            Integer id = jdbcTemplate.query(
                    "SELECT id FROM categories WHERE name = ? LIMIT 1",
                    rs -> rs.next() ? rs.getInt(1) : null,
                    name);
            if (id != null) {
                ids.put(name, id);
            }
        }
        return ids;
    }

    private int remapByLegacyCategoryName(Map<String, Integer> canonicalIds) {
        int updated = 0;
        List<Map<String, Object>> cats = jdbcTemplate.queryForList("SELECT id, name FROM categories");
        for (Map<String, Object> cat : cats) {
            int catId = ((Number) cat.get("id")).intValue();
            String name = String.valueOf(cat.get("name"));
            String target = resolveTargetName(name);
            if (target == null) {
                continue;
            }
            Integer targetId = canonicalIds.get(target);
            if (targetId == null || targetId == catId) {
                continue;
            }
            updated += jdbcTemplate.update(
                    "UPDATE products SET category_id = ? WHERE category_id = ?",
                    targetId, catId);
        }
        return updated;
    }

    private String resolveTargetName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String trimmed = rawName.trim();
        for (String canonical : CANONICAL) {
            if (canonical.equalsIgnoreCase(trimmed)) {
                return canonical;
            }
        }
        return NAME_MERGE.get(trimmed.toLowerCase(Locale.ROOT));
    }

    private int remapByCatalogJson(Map<String, Integer> canonicalIds) {
        try {
            ClassPathResource resource = new ClassPathResource("catalog/vn_top200_products.json");
            if (!resource.exists()) {
                return 0;
            }
            List<Map<String, Object>> rows;
            try (InputStream in = resource.getInputStream()) {
                rows = objectMapper.readValue(in, new TypeReference<>() {
                });
            }
            int updated = 0;
            for (Map<String, Object> row : rows) {
                Object codeObj = row.get("code");
                Object categoryObj = row.get("category");
                if (codeObj == null || categoryObj == null) {
                    continue;
                }
                String code = String.valueOf(codeObj).trim();
                String category = String.valueOf(categoryObj).trim();
                if (code.isEmpty() || category.isEmpty()) {
                    continue;
                }
                Integer categoryId = canonicalIds.get(category);
                if (categoryId == null) {
                    String mapped = resolveTargetName(category);
                    categoryId = mapped == null ? null : canonicalIds.get(mapped);
                }
                if (categoryId == null) {
                    continue;
                }
                updated += jdbcTemplate.update(
                        "UPDATE products SET category_id = ? WHERE code = ?",
                        categoryId, code);
            }
            return updated;
        } catch (Exception ex) {
            log.warn("JSON category remap skipped: {}", ex.getMessage());
            return 0;
        }
    }

    private int deleteEmptyNonCanonical() {
        int deleted = 0;
        List<Map<String, Object>> cats = jdbcTemplate.queryForList("SELECT id, name FROM categories");
        for (Map<String, Object> cat : cats) {
            int catId = ((Number) cat.get("id")).intValue();
            String name = String.valueOf(cat.get("name"));
            boolean canonical = false;
            for (String c : CANONICAL) {
                if (c.equalsIgnoreCase(name)) {
                    canonical = true;
                    break;
                }
            }
            if (canonical) {
                continue;
            }
            Integer productCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM products WHERE category_id = ?",
                    Integer.class, catId);
            if (productCount != null && productCount == 0) {
                try {
                    jdbcTemplate.update("UPDATE categories SET parent_id = NULL WHERE parent_id = ?", catId);
                } catch (Exception ignored) {
                    // parent_id may not exist
                }
                deleted += jdbcTemplate.update("DELETE FROM categories WHERE id = ?", catId);
            }
        }
        return deleted;
    }

    private boolean tableExists(String table) {
        Integer n = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.TABLES
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                        """,
                Integer.class, table);
        return n != null && n > 0;
    }
}
