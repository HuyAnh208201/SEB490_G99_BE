package base.api.feature.customer.util;

public final class CustomerPhoneNormalizer {

    private CustomerPhoneNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+84")) {
            digits = "0" + digits.substring(3);
        } else if (digits.startsWith("84") && digits.length() >= 11) {
            digits = "0" + digits.substring(2);
        }
        return digits.replace("+", "");
    }

    public static boolean isValidVnMobile(String normalized) {
        return normalized != null && normalized.matches("^0\\d{9,10}$");
    }
}
