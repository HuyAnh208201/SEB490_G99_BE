package base.api.shared.util;

import java.util.List;

/**
 * Generates unique EAN-13 barcodes using Vietnam prefix 893 (GS1 country code).
 */
public final class Ean13BarcodeGenerator {

    public static final String VIETNAM_PREFIX = "893";

    private Ean13BarcodeGenerator() {
    }

    public static String nextBarcode(List<String> existingBarcodes) {
        long maxSequence = 0;
        for (String barcode : existingBarcodes) {
            if (barcode == null || !barcode.matches("\\d{13}")) {
                continue;
            }
            if (!barcode.startsWith(VIETNAM_PREFIX)) {
                continue;
            }
            try {
                long sequence = Long.parseLong(barcode.substring(3, 12));
                maxSequence = Math.max(maxSequence, sequence);
            } catch (NumberFormatException ignored) {
                // skip malformed values
            }
        }

        long next = maxSequence + 1;
        if (next > 999_999_999L) {
            throw new IllegalStateException("Barcode sequence exhausted for prefix 893.");
        }

        String body = VIETNAM_PREFIX + String.format("%09d", next);
        return body + computeCheckDigit(body);
    }

    static int computeCheckDigit(String twelveDigits) {
        if (twelveDigits == null || twelveDigits.length() != 12) {
            throw new IllegalArgumentException("EAN-13 body must be 12 digits.");
        }
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = twelveDigits.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        return (10 - (sum % 10)) % 10;
    }
}
