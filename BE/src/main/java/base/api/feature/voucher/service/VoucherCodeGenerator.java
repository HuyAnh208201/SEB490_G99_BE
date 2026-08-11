package base.api.feature.voucher.service;

import base.api.feature.posorder.repository.VoucherRepository;
import base.api.shared.exception.ConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

/** Sinh mã giảm giá duy nhất. Dùng chung cho phát mã thủ công và đổi điểm lấy mã. */
@Component
public class VoucherCodeGenerator {

    /** Bỏ 0/O/1/I để cashier không đọc nhầm khi gõ lại mã cho khách. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int BODY_LENGTH = 8;
    public static final String DEFAULT_PREFIX = "VC";
    /** Đụng unique key là chuyện hiếm; vài lần thử là đủ, hơn nữa là dấu hiệu hỏng. */
    private static final int ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();

    @Autowired
    private VoucherRepository voucherRepository;

    /** Chuẩn hoá tiền tố do người dùng nhập; rỗng hoặc toàn ký tự lạ thì dùng mặc định. */
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
