package base.api.shared.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds loose LIKE patterns so "coca cola" matches "Coca-Cola" etc.
 */
public final class ProductSearchNormalizer {

    private ProductSearchNormalizer() {
    }

    /**
     * Returns a LIKE pattern with % between alphanumeric tokens, or null if blank.
     * Example: "coca cola" / "coca-cola" → "%coca%cola%"
     */
    public static String toLooseLikePattern(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] parts = trimmed.split("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                tokens.add(part);
            }
        }
        if (tokens.isEmpty()) {
            return "%" + trimmed + "%";
        }
        return "%" + String.join("%", tokens) + "%";
    }
}
