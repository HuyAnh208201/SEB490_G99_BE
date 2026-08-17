package base.api.feature.customer.service;

import base.api.feature.customer.repository.CustomerEmailOtpTokenRepository;
import base.api.shared.config.EmailService;
import base.api.shared.entity.CustomerEmailOtpTokenModel;
import base.api.shared.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class CustomerOtpService {

    private static final String REGISTER = "REGISTER";
    private static final String RESET_PASSWORD = "RESET_PASSWORD";

    private final CustomerEmailOtpTokenRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    @Value("${otp.mock:false}")
    private boolean mockMode;
    @Value("${otp.mock-code:123456}")
    private String mockCode;
    @Value("${otp.ttl-seconds:300}")
    private int ttlSeconds;
    @Value("${otp.resend-cooldown-seconds:45}")
    private int resendCooldownSeconds;
    @Value("${otp.max-attempts:5}")
    private int maxAttempts;

    public CustomerOtpService(
            CustomerEmailOtpTokenRepository repository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void issueRegistration(String email, CustomerRegistrationPayload payload) {
        try {
            issue(email, REGISTER, objectMapper.writeValueAsString(payload));
        } catch (BadRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BadRequestException("Unable to start registration");
        }
    }

    @Transactional
    public CustomerRegistrationPayload verifyRegistration(String email, String otp) {
        CustomerEmailOtpTokenModel token = verify(email, REGISTER, otp);
        try {
            return objectMapper.readValue(token.getPayload(), CustomerRegistrationPayload.class);
        } catch (Exception exception) {
            throw new BadRequestException("Registration data is invalid");
        }
    }

    @Transactional
    public void issuePasswordReset(String email) {
        issue(email, RESET_PASSWORD, null);
    }

    @Transactional
    public void verifyPasswordReset(String email, String otp) {
        verify(email, RESET_PASSWORD, otp);
    }

    private void issue(String email, String purpose, String payload) {
        LocalDateTime now = LocalDateTime.now();
        repository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(email, purpose)
                .ifPresent(existing -> {
                    if (existing.getCreatedAt().plusSeconds(resendCooldownSeconds).isAfter(now)) {
                        throw new BadRequestException("Please wait before requesting another code");
                    }
                    existing.setConsumedAt(now);
                    repository.save(existing);
                });

        String code = mockMode ? mockCode : String.format("%06d", random.nextInt(1_000_000));
        CustomerEmailOtpTokenModel token = new CustomerEmailOtpTokenModel();
        token.setEmail(email);
        token.setPurpose(purpose);
        token.setCodeHash(passwordEncoder.encode(code));
        token.setPayload(payload);
        token.setExpiresAt(now.plusSeconds(ttlSeconds));
        token.setCreatedAt(now);
        repository.save(token);

        String subject = REGISTER.equals(purpose)
                ? "ChainStore - Confirm your account"
                : "ChainStore - Password reset code";
        String html = "<p>Your ChainStore verification code is:</p><h1>" + code
                + "</h1><p>This code expires shortly. Do not share it.</p>";
        try {
            emailService.sendHtmlEmailSync(email, subject, html);
        } catch (MessagingException exception) {
            if (!mockMode) {
                throw new BadRequestException("Unable to send verification code. Please try again later.");
            }
        }
    }

    private CustomerEmailOtpTokenModel verify(String email, String purpose, String otp) {
        CustomerEmailOtpTokenModel token = repository
                .findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> new BadRequestException(
                        "No active verification code. Please request a new one."));
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Verification code expired. Please request a new one.");
        }
        if (token.getAttempts() >= maxAttempts) {
            throw new BadRequestException("Too many invalid attempts. Please request a new code.");
        }
        if (!passwordEncoder.matches(otp, token.getCodeHash()) && !(mockMode && mockCode.equals(otp))) {
            token.setAttempts(token.getAttempts() + 1);
            repository.save(token);
            throw new BadRequestException("Invalid verification code");
        }
        token.setConsumedAt(LocalDateTime.now());
        return repository.save(token);
    }
}
