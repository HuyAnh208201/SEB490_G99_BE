package base.api.feature.auth.service.impl;

import base.api.feature.auth.dto.request.ChangePasswordDto;
import base.api.feature.auth.repository.CriticalUserActionTokenRepository;
import base.api.feature.auth.repository.IEmailVerificationTokenRepository;
import base.api.feature.auth.repository.IPasswordResetTokenRepository;
import base.api.feature.auth.repository.IRoleRepository;
import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.shift.repository.ShiftAssignmentRepository;
import base.api.feature.shiftsession.repository.ShiftSessionRepository;
import base.api.shared.config.EmailService;
import base.api.shared.entity.UserModel;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserService#changePassword} — wrong old password, mismatch, length, success.
 */
@ExtendWith(MockitoExtension.class)
class UserChangePasswordTest {

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

    @Test
    void changePasswordSucceedsWhenOldPasswordMatches() throws Exception {
        UserModel user = user(1L, "encoded-old");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass1", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("NewPass1")).thenReturn("encoded-new");

        service.changePassword(1L, dto("OldPass1", "NewPass1", "NewPass1"));

        verify(userRepository).save(user);
        verify(passwordEncoder).encode("NewPass1");
    }

    @Test
    void changePasswordRejectsUnknownUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        Exception error = assertThrows(
                Exception.class, () -> service.changePassword(99L, dto("a", "abcdef", "abcdef")));

        assertTrue(error.getMessage().contains("Không tìm thấy người dùng"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        UserModel user = user(1L, "encoded-old");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-old")).thenReturn(false);

        Exception error = assertThrows(
                Exception.class, () -> service.changePassword(1L, dto("wrong", "NewPass1", "NewPass1")));

        assertTrue(error.getMessage().contains("Mật khẩu cũ không đúng"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordRejectsConfirmMismatch() {
        UserModel user = user(1L, "encoded-old");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass1", "encoded-old")).thenReturn(true);

        Exception error = assertThrows(
                Exception.class, () -> service.changePassword(1L, dto("OldPass1", "NewPass1", "Other1")));

        assertTrue(error.getMessage().contains("Mật khẩu xác nhận không khớp"));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void changePasswordRejectsShortNewPassword() {
        UserModel user = user(1L, "encoded-old");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass1", "encoded-old")).thenReturn(true);

        Exception error = assertThrows(
                Exception.class, () -> service.changePassword(1L, dto("OldPass1", "12345", "12345")));

        assertTrue(error.getMessage().contains("Mật khẩu phải có ít nhất 6 ký tự"));
        verify(userRepository, never()).save(any());
    }

    private static ChangePasswordDto dto(String oldPassword, String newPassword, String confirm) {
        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setOldPassword(oldPassword);
        dto.setNewPassword(newPassword);
        dto.setConfirmNewPassword(confirm);
        return dto;
    }

    private static UserModel user(Long id, String password) {
        UserModel user = new UserModel();
        user.setId(id);
        user.setPassword(password);
        return user;
    }
}
