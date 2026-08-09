package base.api.feature.auth.service.impl;

import base.api.feature.auth.dto.request.RegisterDto;
import base.api.feature.auth.repository.CriticalUserActionTokenRepository;
import base.api.feature.auth.repository.IEmailVerificationTokenRepository;
import base.api.feature.auth.repository.IPasswordResetTokenRepository;
import base.api.feature.auth.repository.IRoleRepository;
import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.shift.repository.ShiftAssignmentRepository;
import base.api.feature.shiftsession.repository.ShiftSessionRepository;
import base.api.shared.config.EmailService;
import base.api.shared.config.VerificationCodeIssuer;
import base.api.shared.entity.EmailVerificationTokenModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import base.api.shared.security.CurrentUserProvider;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserService#registerUser} — blank phone, duplicates, happy path, email failure.
 */
@ExtendWith(MockitoExtension.class)
class UserRegisterTest {

    @Mock private IUserRepository userRepository;
    @Mock private IRoleRepository roleRepository;
    @Mock private IBranchRepository branchRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private IPasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private IEmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock private EmailService emailService;
    @Mock private VerificationCodeIssuer verificationCodeIssuer;
    @Mock private CriticalUserActionTokenRepository criticalUserActionTokenRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private ShiftSessionRepository shiftSessionRepository;
    @Mock private ShiftAssignmentRepository shiftAssignmentRepository;

    @InjectMocks
    private UserService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "apiBaseUrl", "http://localhost:1328");
    }

    @Test
    void registerUserRejectsBlankPhone() {
        RegisterDto dto = validDto();
        dto.setPhone("   ");

        Exception error = assertThrows(IllegalArgumentException.class, () -> service.registerUser(dto));

        assertTrue(error.getMessage().contains("Số điện thoại không được để trống"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUserRejectsNullPhone() {
        RegisterDto dto = validDto();
        dto.setPhone(null);

        Exception error = assertThrows(IllegalArgumentException.class, () -> service.registerUser(dto));

        assertTrue(error.getMessage().contains("Số điện thoại không được để trống"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUserRejectsDuplicateUsername() {
        RegisterDto dto = validDto();
        doReturn(true).when(userRepository).existsByUserName("newuser");

        Exception error = assertThrows(IllegalArgumentException.class, () -> service.registerUser(dto));

        assertTrue(error.getMessage().contains("Username đã tồn tại"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUserRejectsDuplicateEmail() {
        RegisterDto dto = validDto();
        doReturn(false).when(userRepository).existsByUserName("newuser");
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(true);

        Exception error = assertThrows(IllegalArgumentException.class, () -> service.registerUser(dto));

        assertTrue(error.getMessage().contains("Email đã được sử dụng"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUserSucceedsAndSendsVerificationEmail() throws Exception {
        RegisterDto dto = validDto();
        doReturn(false).when(userRepository).existsByUserName("newuser");
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret1")).thenReturn("encoded-secret");
        when(userRepository.save(any(UserModel.class))).thenAnswer(call -> {
            UserModel user = call.getArgument(0);
            user.setId(42L);
            return user;
        });
        when(emailVerificationTokenRepository.save(any(EmailVerificationTokenModel.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(verificationCodeIssuer.issueCode()).thenReturn("123456");

        UserModel saved = service.registerUser(dto);

        assertNotNull(saved.getId());
        assertEquals(42L, saved.getId());
        // UserModel.getUserName() is backed by email in this domain model.
        assertEquals("newuser@example.com", saved.getUserName());
        assertEquals("newuser", saved.userName);
        assertEquals("newuser@example.com", saved.getEmail());
        assertEquals(UserRole.CUSTOMER, saved.getRole());
        verify(emailVerificationTokenRepository).save(any(EmailVerificationTokenModel.class));
        verify(emailService).sendHtmlEmail(eq("newuser@example.com"), anyString(), anyString());
    }

    @Test
    void registerUserThrowsWhenVerificationEmailFails() throws Exception {
        RegisterDto dto = validDto();
        doReturn(false).when(userRepository).existsByUserName("newuser");
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret1")).thenReturn("encoded-secret");
        when(userRepository.save(any(UserModel.class))).thenAnswer(call -> {
            UserModel user = call.getArgument(0);
            user.setId(42L);
            return user;
        });
        when(emailVerificationTokenRepository.save(any(EmailVerificationTokenModel.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(verificationCodeIssuer.issueCode()).thenReturn("123456");
        when(verificationCodeIssuer.isMock()).thenReturn(false);
        doThrow(new MessagingException("SMTP down"))
                .when(emailService).sendHtmlEmail(anyString(), anyString(), anyString());

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.registerUser(dto));

        assertTrue(error.getMessage().contains("Không thể gửi email xác thực. Vui lòng thử lại sau."));
    }

    private static RegisterDto validDto() {
        RegisterDto dto = new RegisterDto();
        dto.setUserName("NewUser");
        dto.setPassword("Secret1");
        dto.setEmail("NewUser@Example.com");
        dto.setFirstName("Lan");
        dto.setLastName("Nguyen");
        dto.setPhone("0912345678");
        dto.setRole(UserRole.CUSTOMER);
        return dto;
    }
}
