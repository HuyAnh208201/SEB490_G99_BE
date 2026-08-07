package base.api.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Issues 6-digit email verification codes.
 * When {@code otp.mock=true}, always returns {@code otp.mock-code} so local/demo
 * flows work without reading a real inbox (SMTP failure is soft).
 */
@Component
public class VerificationCodeIssuer {

    private final SecureRandom random = new SecureRandom();

    @Value("${otp.mock:true}")
    private boolean mockMode;

    @Value("${otp.mock-code:123456}")
    private String mockCode;

    public boolean isMock() {
        return mockMode;
    }

    public String issueCode() {
        if (mockMode) {
            return mockCode == null || mockCode.isBlank() ? "123456" : mockCode.trim();
        }
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
