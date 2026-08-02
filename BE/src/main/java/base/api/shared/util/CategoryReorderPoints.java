package base.api.shared.util;

/**
 * Reorder points by retail category for VN convenience assortment.
 * Warehouse buffers are larger than branch shelf buffers.
 * Values are retail units; {@code pack} = {@code units_per_import_unit}.
 */
public final class CategoryReorderPoints {

    private CategoryReorderPoints() {
    }

    /** @deprecated use {@link #forWarehouse(String, Integer)} */
    @Deprecated
    public static int forCategory(String categoryName, Integer unitsPerImportUnit) {
        return forWarehouse(categoryName, unitsPerImportUnit);
    }

    /**
     * Central warehouse buffer (WM / low-stock at warehouse).
     */
    public static int forWarehouse(String categoryName, Integer unitsPerImportUnit) {
        int pack = normalizePack(unitsPerImportUnit);
        String cat = normalizeCategory(categoryName);

        int base = switch (cat) {
            case "drinks", "instant food" -> pack * 3;
            case "snacks", "dairy", "personal care", "tobacco & beer", "ice cream" -> pack * 2;
            case "household" -> Math.max(pack, 12);
            case "bakery" -> Math.max(pack, 15);
            case "cosmetics", "alcohol" -> Math.max(pack, 6);
            case "top-up / digital" -> 50;
            default -> pack * 2;
        };
        return Math.max(base, 1);
    }

    /**
     * Branch shelf buffer (BM import-request recommendations).
     * Smaller than warehouse — typically ~1 import pack on shelf.
     */
    public static int forBranch(String categoryName, Integer unitsPerImportUnit) {
        int pack = normalizePack(unitsPerImportUnit);
        String cat = normalizeCategory(categoryName);

        int base = switch (cat) {
            case "drinks", "instant food", "snacks", "dairy",
                 "personal care", "tobacco & beer", "ice cream" -> pack;
            case "household" -> Math.max(pack, 8);
            case "bakery" -> Math.max(pack, 10);
            case "cosmetics", "alcohol" -> Math.max(pack / 2, 3);
            case "top-up / digital" -> 20;
            default -> pack;
        };
        return Math.max(base, 1);
    }

    private static int normalizePack(Integer unitsPerImportUnit) {
        return unitsPerImportUnit == null || unitsPerImportUnit < 1 ? 12 : unitsPerImportUnit;
    }

    private static String normalizeCategory(String categoryName) {
        return categoryName == null ? "" : categoryName.trim().toLowerCase();
    }
}
