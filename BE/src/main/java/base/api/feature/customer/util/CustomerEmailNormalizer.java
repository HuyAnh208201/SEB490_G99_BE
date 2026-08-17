package base.api.feature.customer.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class CustomerEmailNormalizer {

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private CustomerEmailNormalizer() {
    }

    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String normalized) {
        return normalized != null
                && !normalized.endsWith("@customer.chainstore.local")
                && EMAIL.matcher(normalized).matches();
    }
}
