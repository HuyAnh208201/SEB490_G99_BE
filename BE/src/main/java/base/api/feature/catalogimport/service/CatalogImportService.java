package base.api.feature.catalogimport.service;

import base.api.feature.catalogimport.dto.CatalogImportStatusResponse;
import base.api.shared.exception.BadRequestException;
import base.api.shared.util.CategoryReorderPoints;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-shot slow catalog import (mentor guidance): Admin starts via API;
 * async job upserts SKUs one-by-one with delay. Never runs on ApplicationRunner.
 */
@Service
public class CatalogImportService {

    private static final Logger log = LoggerFactory.getLogger(CatalogImportService.class);
    private static final String RESOURCE = "catalog/vn_market_extra_products.json";
    private static final long DELAY_MS = 80L;
    private static final int BRANCH_STOCK = 30;
    private static final int WAREHOUSE_STOCK = 100;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile String status = "IDLE";
    private volatile int total;
    private volatile int created;
    private volatile int skipped;
    private volatile int failed;
    private volatile int processed;
    private volatile String message = "Idle";
    private volatile String startedAt;
    private volatile String finishedAt;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CatalogImportJobRunner jobRunner;

    public CatalogImportStatusResponse status() {
        CatalogImportStatusResponse response = new CatalogImportStatusResponse();
        response.setStatus(status);
        response.setTotal(total);
        response.setCreated(created);
        response.setSkipped(skipped);
        response.setFailed(failed);
        response.setProcessed(processed);
        response.setMessage(message);
        response.setStartedAt(startedAt);
        response.setFinishedAt(finishedAt);
        return response;
    }

    public CatalogImportStatusResponse start() {
        if (!running.compareAndSet(false, true)) {
            throw new BadRequestException("Catalog import is already running.");
        }
        status = "RUNNING";
        total = 0;
        created = 0;
        skipped = 0;
        failed = 0;
        processed = 0;
        message = "Import started";
        startedAt = Instant.now().toString();
        finishedAt = null;
        jobRunner.run();
        return status();
    }

    /** Invoked by {@link CatalogImportJobRunner} on the async executor. */
    public void executeImport() {
        try {
            List<CatalogSku> skus = loadSkus();
            total = skus.size();
            Map<String, Integer> categoryIds = loadCategoryIds();
            for (CatalogSku sku : skus) {
                try {
                    ensureCategory(sku.category(), categoryIds);
                    boolean inserted = upsert(sku, categoryIds);
                    if (inserted) {
                        created++;
                    } else {
                        skipped++;
                    }
                } catch (Exception ex) {
                    failed++;
                    log.warn("Catalog import failed for {}: {}", sku.code(), ex.getMessage());
                }
                processed++;
                message = "Processed " + processed + "/" + total;
                Thread.sleep(DELAY_MS);
            }
            status = "COMPLETED";
            message = "Import completed";
            finishedAt = Instant.now().toString();
            log.info("Catalog import completed: created={} skipped={} failed={} total={}",
                    created, skipped, failed, total);
        } catch (Exception ex) {
            status = "FAILED";
            message = ex.getMessage() == null ? "Import failed" : ex.getMessage();
            finishedAt = Instant.now().toString();
            log.warn("Catalog import failed: {}", ex.getMessage(), ex);
        } finally {
            running.set(false);
        }
    }

    private List<CatalogSku> loadSkus() throws Exception {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<>() {
            });
        }
    }

    private Map<String, Integer> loadCategoryIds() {
        Map<String, Integer> map = new HashMap<>();
        jdbc.query("SELECT id, name FROM categories", rs -> {
            map.put(rs.getString("name"), rs.getInt("id"));
        });
        return map;
    }

    private void ensureCategory(String name, Map<String, Integer> categoryIds) {
        if (name == null || name.isBlank() || categoryIds.containsKey(name)) {
            return;
        }
        jdbc.update("""
                INSERT INTO categories (name, parent_id, description)
                SELECT ?, NULL, ?
                WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = ?)
                """, name, name + " products for convenience stores", name);
        categoryIds.clear();
        categoryIds.putAll(loadCategoryIds());
    }

    /** @return true if created, false if product already existed (inventory still repaired). */
    private boolean upsert(CatalogSku sku, Map<String, Integer> categoryIds) {
        Integer categoryId = categoryIds.get(sku.category());
        if (categoryId == null) {
            throw new IllegalStateException("Missing category " + sku.category());
        }

        Integer existingByCode = queryInt("SELECT id FROM products WHERE code = ?", sku.code());
        Integer productId = existingByCode;
        boolean created = false;

        if (productId == null) {
            Integer existingByBarcode = sku.barcode() == null || sku.barcode().isBlank()
                    ? null
                    : queryInt("SELECT id FROM products WHERE barcode = ?", sku.barcode());
            if (existingByBarcode != null) {
                productId = existingByBarcode;
            }
        }

        if (productId == null) {
            jdbc.update("""
                    INSERT INTO products
                        (code, barcode, name, category_id, unit, import_unit, units_per_import_unit,
                         reference_import_price, default_sale_price, scope, status, description)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'GLOBAL', 'active', ?)
                    """,
                    sku.code(),
                    blankToNull(sku.barcode()),
                    sku.name(),
                    categoryId,
                    sku.unit(),
                    blankToNull(sku.importUnit()),
                    sku.unitsPerImportUnit(),
                    sku.referenceImportPrice(),
                    sku.defaultSalePrice(),
                    blankToNull(sku.description()));
            productId = queryInt("SELECT id FROM products WHERE code = ?", sku.code());
            if (productId == null) {
                throw new IllegalStateException("Insert did not return id for " + sku.code());
            }
            created = true;
        }

        ensureInventoryAndPackaging(productId, sku);
        return created;
    }

    private void ensureInventoryAndPackaging(Integer productId, CatalogSku sku) {
        int branchReorder = CategoryReorderPoints.forBranch(sku.category(), sku.unitsPerImportUnit());
        int warehouseReorder = CategoryReorderPoints.forWarehouse(sku.category(), sku.unitsPerImportUnit());

        jdbc.update("""
                INSERT INTO warehouse_inventory (product_id, quantity, reorder_point, updated_at)
                SELECT ?, ?, ?, NOW()
                WHERE NOT EXISTS (SELECT 1 FROM warehouse_inventory WHERE product_id = ?)
                """, productId, WAREHOUSE_STOCK, warehouseReorder, productId);

        jdbc.update("""
                INSERT INTO branch_inventory (branch_id, product_id, quantity, reorder_point, updated_at)
                SELECT 1, ?, ?, ?, NOW()
                WHERE NOT EXISTS (
                    SELECT 1 FROM branch_inventory WHERE branch_id = 1 AND product_id = ?
                )
                """, productId, BRANCH_STOCK, branchReorder, productId);

        String baseLabel = sku.unit() == null || sku.unit().isBlank() ? "Unit" : capitalize(sku.unit());
        Integer upu = sku.unitsPerImportUnit() == null ? 1 : sku.unitsPerImportUnit();
        boolean singleLevel = upu <= 1 || sku.importUnit() == null || sku.importUnit().isBlank();
        jdbc.update("""
                INSERT INTO product_packagings
                    (product_id, code, name, label_en, conversion_qty, is_base, is_purchase_default, sort_order)
                SELECT ?, 'base', ?, ?, 1, 1, ?, 0
                WHERE NOT EXISTS (SELECT 1 FROM product_packagings WHERE product_id = ?)
                """, productId, baseLabel, baseLabel, singleLevel ? 1 : 0, productId);
        if (!singleLevel) {
            String topLabel = capitalize(sku.importUnit()) + " of " + upu;
            jdbc.update("""
                    INSERT INTO product_packagings
                        (product_id, code, name, label_en, conversion_qty, is_base, is_purchase_default, sort_order)
                    SELECT ?, 'top', ?, ?, ?, 0, 1, ?
                    WHERE NOT EXISTS (
                        SELECT 1 FROM product_packagings WHERE product_id = ? AND code = 'top'
                    )
                    """, productId, topLabel, topLabel, upu, upu, productId);
        }
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    private Integer queryInt(String sql, Object... args) {
        List<Integer> rows = jdbc.query(sql, (rs, rowNum) -> rs.getInt(1), args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record CatalogSku(
            String code,
            String barcode,
            String name,
            String category,
            String unit,
            String importUnit,
            Integer unitsPerImportUnit,
            BigDecimal referenceImportPrice,
            BigDecimal defaultSalePrice,
            String description
    ) {
    }
}
