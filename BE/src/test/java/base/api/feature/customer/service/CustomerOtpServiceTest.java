package base.api.feature.customer.service;

import base.api.feature.customer.repository.CustomerEmailOtpTokenRepository;
import base.api.shared.config.EmailService;
import base.api.shared.entity.CustomerEmailOtpTokenModel;
import base.api.shared.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerOtpServiceTest {

    @Mock
    private CustomerEmailOtpTokenRepository repository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;

    private CustomerOtpService service;

    @BeforeEach
    void setUp() {
        service = new CustomerOtpService(repository, passwordEncoder, emailService, new ObjectMapper());
        ReflectionTestUtils.setField(service, "mockMode", true);
        ReflectionTestUtils.setField(service, "mockCode", "123456");
        ReflectionTestUtils.setField(service, "ttlSeconds", 300);
        ReflectionTestUtils.setField(service, "resendCooldownSeconds", 45);
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
    }

    @Test
    void issuesAndVerifiesRegistrationPayload() throws Exception {
        when(repository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("123456")).thenReturn("hash");
        when(repository.save(any(CustomerEmailOtpTokenModel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CustomerRegistrationPayload payload =
                new CustomerRegistrationPayload("0912345678", "Customer", "password-hash");

        service.issueRegistration("customer@example.com", payload);
        verify(emailService).sendHtmlEmailSync(any(), any(), any());

        CustomerEmailOtpTokenModel token = token("REGISTER", false, 0);
        token.setPayload(new ObjectMapper().writeValueAsString(payload));
        when(repository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "customer@example.com", "REGISTER")).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);

        CustomerRegistrationPayload verified = service.verifyRegistration("customer@example.com", "123456");

        assertEquals("0912345678", verified.phone());
        assertEquals("Customer", verified.fullName());
    }

    @Test
    void blocksCooldownAndExpiredOrRepeatedInvalidCodes() {
        CustomerEmailOtpTokenModel recent = token("REGISTER", false, 0);
        recent.setCreatedAt(LocalDateTime.now());
        when(repository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "customer@example.com", "REGISTER")).thenReturn(Optional.of(recent));
        assertThrows(BadRequestException.class, () -> service.issueRegistration(
                "customer@example.com", new CustomerRegistrationPayload("09", "Name", "hash")));

        CustomerEmailOtpTokenModel expired = token("RESET_PASSWORD", true, 0);
        when(repository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "customer@example.com", "RESET_PASSWORD")).thenReturn(Optional.of(expired));
        assertThrows(BadRequestException.class,
                () -> service.verifyPasswordReset("customer@example.com", "123456"));

        CustomerEmailOtpTokenModel exhausted = token("RESET_PASSWORD", false, 5);
        when(repository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "customer@example.com", "RESET_PASSWORD")).thenReturn(Optional.of(exhausted));
        assertThrows(BadRequestException.class,
                () -> service.verifyPasswordReset("customer@example.com", "000000"));
    }

    @Test
    void incrementsAttemptsForInvalidCode() {
        ReflectionTestUtils.setField(service, "mockMode", false);
        CustomerEmailOtpTokenModel token = token("RESET_PASSWORD", false, 0);
        when(repository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "customer@example.com", "RESET_PASSWORD")).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("000000", "hash")).thenReturn(false);

        assertThrows(BadRequestException.class,
                () -> service.verifyPasswordReset("customer@example.com", "000000"));
        assertEquals(1, token.getAttempts());
        verify(repository).save(token);
    }

    private CustomerEmailOtpTokenModel token(String purpose, boolean expired, int attempts) {
        CustomerEmailOtpTokenModel token = new CustomerEmailOtpTokenModel();
        token.setEmail("customer@example.com");
        token.setPurpose(purpose);
        token.setCodeHash("hash");
        token.setAttempts(attempts);
        token.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        token.setExpiresAt(expired ? LocalDateTime.now().minusMinutes(1) : LocalDateTime.now().plusMinutes(5));
        return token;
    }
}
