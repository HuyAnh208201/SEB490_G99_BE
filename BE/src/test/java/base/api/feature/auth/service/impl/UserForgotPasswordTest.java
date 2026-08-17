package base.api.feature.auth.service.impl;

import base.api.feature.auth.dto.request.CompleteForgotPasswordDto;
import base.api.feature.auth.dto.response.InitiateForgotPasswordResponse;
import base.api.feature.auth.repository.CriticalUserActionTokenRepository;
import base.api.feature.auth.repository.IEmailVerificationTokenRepository;
import base.api.feature.auth.repository.IPasswordResetTokenRepository;
import base.api.feature.auth.repository.IRoleRepository;
import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.shift.repository.ShiftAssignmentRepository;
import base.api.feature.shiftsession.repository.ShiftSessionRepository;
import base.api.shared.config.EmailService;
import base.api.shared.entity.PasswordResetTokenModel;
import base.api.shared.entity.UserModel;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for forgot-password initiate and complete flows.
 */
@ExtendWith(MockitoExtension.class)
class UserForgotPasswordTest {

    @Mock private IUserRepository userRepository;
    @Mock private IRoleRepository roleRepository;
    @Mock private IBranchRepository branchRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private IPasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private IEmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock private EmailService emailService;
    @Mock private CriticalUserActionTokenRepository criticalUserActionTokenRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private ShiftSessionRepository shiftSessionRepository;
    @Mock private ShiftAssignmentRepository shiftAssignmentRepository;

    @InjectMocks
    private UserService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "apiBaseUrl", "http://localhost:1328");
        ReflectionTestUtils.setField(service, "clientBaseUrl", "http://localhost:5175");
    }

    // -------------------------------------------------------------------------
    // initiateForgotPassword
    // -------------------------------------------------------------------------

    @Test
    void initiateForgotPasswordRejectsBlankContact() {
        Exception error = assertThrows(IllegalArgumentException.class,
                () -> service.initiateForgotPassword("   "));

        assertTrue(error.getMessage().contains("Thông tin liên hệ không được để trống"));
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void initiateForgotPasswordRejectsNullContact() {
        Exception error = assertThrows(IllegalArgumentException.class,
                () -> service.initiateForgotPassword(null));

        assertTrue(error.getMessage().contains("Thông tin liên hệ không được để trống"));
    }

    @Test
    void initiateForgotPasswordRejectsUnknownUser() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        Exception error = assertThrows(Exception.class,
                () -> service.initiateForgotPassword("missing@example.com"));

        assertTrue(error.getMessage().contains("Không tìm thấy tài khoản với thông tin này"));
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void initiateForgotPasswordSucceedsForKnownEmail() throws Exception {
        UserModel user = user(1L, "alice", "alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.save(any(PasswordResetTokenModel.class)))
                .thenAnswer(call -> call.getArgument(0));

        InitiateForgotPasswordResponse response = service.initiateForgotPassword(
                "alice@example.com", "http://localhost:5175");

        assertTrue(response.getMessage().contains("Link đặt lại mật khẩu đã được gửi"));
        verify(passwordResetTokenRepository).deleteByUserId(1L);
        verify(passwordResetTokenRepository).save(any(PasswordResetTokenModel.class));
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendHtmlEmail(anyString(), anyString(), body.capture());
        assertTrue(body.getValue().contains("http://localhost:5175/reset-password?token="));
    }

    @Test
    void initiateForgotPasswordThrowsWhenEmailSendFails() throws Exception {
        UserModel user = user(1L, "alice", "alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.save(any(PasswordResetTokenModel.class)))
                .thenAnswer(call -> call.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP down"))
                .when(emailService).sendHtmlEmail(anyString(), anyString(), anyString());

        Exception error = assertThrows(Exception.class,
                () -> service.initiateForgotPassword("alice@example.com"));

        assertTrue(error.getMessage().contains("Không thể gửi email. Vui lòng thử lại sau."));
    }

    // -------------------------------------------------------------------------
    // completeForgotPassword
    // -------------------------------------------------------------------------

    @Test
    void completeForgotPasswordRejectsConfirmMismatch() {
        CompleteForgotPasswordDto dto = completeDto("token-1", "NewPass1", "Other1");

        Exception error = assertThrows(Exception.class, () -> service.completeForgotPassword(dto));

        assertTrue(error.getMessage().contains("Mật khẩu xác nhận không khớp"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void completeForgotPasswordRejectsShortPassword() {
        CompleteForgotPasswordDto dto = completeDto("token-1", "12345", "12345");

        Exception error = assertThrows(Exception.class, () -> service.completeForgotPassword(dto));

        assertTrue(error.getMessage().contains("Mật khẩu phải có ít nhất 6 ký tự"));
        verify(passwordResetTokenRepository, never()).findByResetToken(anyString());
    }

    @Test
    void completeForgotPasswordRejectsInvalidToken() {
        CompleteForgotPasswordDto dto = completeDto("bad-token", "NewPass1", "NewPass1");
        when(passwordResetTokenRepository.findByResetToken("bad-token")).thenReturn(Optional.empty());

        Exception error = assertThrows(Exception.class, () -> service.completeForgotPassword(dto));

        assertTrue(error.getMessage().contains("Token không hợp lệ"));
    }

    @Test
    void completeForgotPasswordRejectsUsedToken() {
        CompleteForgotPasswordDto dto = completeDto("used-token", "NewPass1", "NewPass1");
        PasswordResetTokenModel token = validToken("used-token", 1L);
        token.setUsed(true);
        when(passwordResetTokenRepository.findByResetToken("used-token")).thenReturn(Optional.of(token));

        Exception error = assertThrows(Exception.class, () -> service.completeForgotPassword(dto));

        assertTrue(error.getMessage().contains("Token đã được sử dụng"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void completeForgotPasswordRejectsExpiredToken() {
        CompleteForgotPasswordDto dto = completeDto("expired-token", "NewPass1", "NewPass1");
        PasswordResetTokenModel token = validToken("expired-token", 1L);
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(passwordResetTokenRepository.findByResetToken("expired-token")).thenReturn(Optional.of(token));

        Exception error = assertThrows(Exception.class, () -> service.completeForgotPassword(dto));

        assertTrue(error.getMessage().contains("Token đã hết hạn"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void completeForgotPasswordSucceeds() throws Exception {
        CompleteForgotPasswordDto dto = completeDto("good-token", "NewPass1", "NewPass1");
        PasswordResetTokenModel token = validToken("good-token", 1L);
        UserModel user = user(1L, "alice", "alice@example.com");
        when(passwordResetTokenRepository.findByResetToken("good-token")).thenReturn(Optional.of(token));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass1")).thenReturn("encoded-new");
        when(userRepository.save(any(UserModel.class))).thenAnswer(call -> call.getArgument(0));
        when(passwordResetTokenRepository.save(any(PasswordResetTokenModel.class)))
                .thenAnswer(call -> call.getArgument(0));

        service.completeForgotPassword(dto);

        assertEquals("encoded-new", user.getPassword());
        assertTrue(token.isUsed());
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).save(token);
    }

    private static CompleteForgotPasswordDto completeDto(String token, String password, String confirm) {
        CompleteForgotPasswordDto dto = new CompleteForgotPasswordDto();
        dto.setResetToken(token);
        dto.setNewPassword(password);
        dto.setConfirmNewPassword(confirm);
        return dto;
    }

    private static PasswordResetTokenModel validToken(String resetToken, Long userId) {
        PasswordResetTokenModel token = new PasswordResetTokenModel();
        token.setResetToken(resetToken);
        token.setUserId(userId);
        token.setEmail("alice@example.com");
        token.setUsed(false);
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        return token;
    }

    private static UserModel user(Long id, String userName, String email) {
        UserModel user = new UserModel();
        user.setId(id);
        user.setUserName(userName);
        user.setEmail(email);
        user.setFirstName("Alice");
        return user;
    }
}
