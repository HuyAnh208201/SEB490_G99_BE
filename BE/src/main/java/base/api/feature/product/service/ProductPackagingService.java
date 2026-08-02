package base.api.feature.product.service;

import base.api.feature.product.repository.ProductPackagingRepository;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.ProductPackagingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves ordered packaging/UoM levels for a product and converts between
 * the TOP (purchase-request) unit and the BASE unit that stock ledgers
 * (branch_inventory, warehouse_inventory) are kept in.
 *
 * Packaging rows are the source of truth. When a product has no packaging
 * rows yet (e.g. legacy data not migrated), a transient level is synthesized
 * from {@code ProductModel.unit} / {@code importUnit} / {@code unitsPerImportUnit}
 * for backward compatibility.
 */
@Service
public class ProductPackagingService {

    @Autowired
    private ProductPackagingRepository packagingRepository;

    public List<ProductPackagingModel> getOrderedPackagings(Integer productId) {
        return packagingRepository.findByProductIdOrderBySortOrderAscIdAsc(productId);
    }

    /** The TOP/import level used for purchase requests, with legacy fallback. */
    public ProductPackagingModel getTopPackaging(ProductModel product) {
        if (product == null || product.getId() == null) {
            return null;
        }
        return packagingRepository.findFirstByProductIdAndIsPurchaseDefaultTrue(product.getId())
                .orElseGet(() -> fallbackTopPackaging(product));
    }

    /** Bulk lookup for list endpoints. Products without a DB row are omitted (caller can fall back). */
    public Map<Integer, ProductPackagingModel> getTopPackagingsByProductIds(Collection<Integer> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, ProductPackagingModel> result = new HashMap<>();
        for (ProductPackagingModel packaging : packagingRepository.findByProductIdInAndIsPurchaseDefaultTrue(productIds)) {
            result.putIfAbsent(packaging.getProductId(), packaging);
        }
        return result;
    }

    /**
     * In-memory TOP packaging when list endpoints already bulk-loaded DB rows.
     * Avoids N+1: never hit the DB again per product in a catalog response.
     */
    public ProductPackagingModel resolveTopPackaging(
            ProductModel product, Map<Integer, ProductPackagingModel> preloaded) {
        if (product == null || product.getId() == null) {
            return null;
        }
        if (preloaded != null) {
            ProductPackagingModel fromDb = preloaded.get(product.getId());
            if (fromDb != null) {
                return fromDb;
            }
        }
        return fallbackTopPackaging(product);
    }

    /** How many BASE units one TOP unit contains for this product (>= 1). */
    public int topConversionQty(ProductModel product) {
        ProductPackagingModel top = getTopPackaging(product);
        return conversionQtyOf(top);
    }

    public int conversionQtyOf(ProductPackagingModel packaging) {
        if (packaging == null || packaging.getConversionQty() == null || packaging.getConversionQty() < 1) {
            return 1;
        }
        return packaging.getConversionQty();
    }

    /** Convert a quantity expressed in TOP packaging units into BASE units. */
    public int toBaseQty(int topUnitsQty, ProductPackagingModel topPackaging) {
        return Math.max(0, topUnitsQty) * conversionQtyOf(topPackaging);
    }

    public int toBaseQty(int topUnitsQty, ProductModel product) {
        return toBaseQty(topUnitsQty, getTopPackaging(product));
    }

    /** English display label for the TOP packaging (e.g. "Case of 24"). */
    public String topLabel(ProductModel product) {
        ProductPackagingModel top = getTopPackaging(product);
        return top == null ? null : top.displayLabel();
    }

    /**
     * Creates base + top packaging rows for a product that has none yet, mirroring
     * unit / importUnit / unitsPerImportUnit. Safe to call on every create/update:
     * it is a no-op once packaging rows exist, so hand-crafted levels are preserved.
     */
    public void ensureDefaultPackagings(ProductModel product) {
        if (product == null || product.getId() == null || packagingRepository.existsByProductId(product.getId())) {
            return;
        }
        String baseLabel = capitalize(product.getUnit());
        Integer unitsPerImportUnit = product.getUnitsPerImportUnit();
        boolean singleLevel = unitsPerImportUnit == null || unitsPerImportUnit <= 1 || product.getImportUnit() == null;

        ProductPackagingModel base = new ProductPackagingModel();
        base.setProductId(product.getId());
        base.setCode("base");
        base.setName(baseLabel);
        base.setLabelEn(baseLabel);
        base.setConversionQty(1);
        base.setIsBase(true);
        base.setIsPurchaseDefault(singleLevel);
        base.setSortOrder(0);
        packagingRepository.save(base);

        if (!singleLevel) {
            String topLabel = capitalize(product.getImportUnit()) + " of " + unitsPerImportUnit;
            ProductPackagingModel top = new ProductPackagingModel();
            top.setProductId(product.getId());
            top.setCode("top");
            top.setName(topLabel);
            top.setLabelEn(topLabel);
            top.setConversionQty(unitsPerImportUnit);
            top.setIsBase(false);
            top.setIsPurchaseDefault(true);
            top.setSortOrder(unitsPerImportUnit);
            packagingRepository.save(top);
        }
    }

    private ProductPackagingModel fallbackTopPackaging(ProductModel product) {
        ProductPackagingModel synthesized = new ProductPackagingModel();
        synthesized.setProductId(product.getId());
        Integer unitsPerImportUnit = product.getUnitsPerImportUnit();
        if (unitsPerImportUnit != null && unitsPerImportUnit > 1 && product.getImportUnit() != null) {
            synthesized.setConversionQty(unitsPerImportUnit);
            synthesized.setLabelEn(capitalize(product.getImportUnit()) + " of " + unitsPerImportUnit);
            synthesized.setName(synthesized.getLabelEn());
            synthesized.setIsBase(false);
        } else {
            synthesized.setConversionQty(1);
            synthesized.setLabelEn(capitalize(product.getUnit()));
            synthesized.setName(synthesized.getLabelEn());
            synthesized.setIsBase(true);
        }
        synthesized.setIsPurchaseDefault(true);
        return synthesized;
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Unit";
        }
        String trimmed = value.trim();
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1).toLowerCase(Locale.ROOT);
    }
}
