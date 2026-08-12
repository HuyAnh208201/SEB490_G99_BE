package base.api.feature.voucher.service;

import base.api.feature.posorder.repository.VoucherRepository;
import base.api.shared.exception.ConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

/** Generates unique voucher codes, shared by manual issuing and point redemption. */
@Component
public class VoucherCodeGenerator {

    /** 0/O/1/I are left out so a cashier cannot misread a code when typing it back in. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int BODY_LENGTH = 8;
    public static final String DEFAULT_PREFIX = "VC";
    /** Hitting the unique key is rare; a few retries suffice, more means something is wrong. */
    private static final int ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();

    @Autowired
    private VoucherRepository voucherRepository;

    /** Normalises a user-supplied prefix; empty or all-invalid falls back to the default. */
    public String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return DEFAULT_PREFIX;
        }
        String normalized = prefix.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalized.isEmpty() ? DEFAULT_PREFIX : normalized;
    }

    public String generate(String prefix) {
        String safePrefix = normalizePrefix(prefix);
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder(safePrefix);
            for (int i = 0; i < BODY_LENGTH; i++) {
                code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String candidate = code.toString();
            if (!voucherRepository.existsByCodeIgnoreCase(candidate)) {
                return candidate;
            }
        }
        throw new ConflictException("Could not generate a unique code. Try a different prefix.");
    }
}
