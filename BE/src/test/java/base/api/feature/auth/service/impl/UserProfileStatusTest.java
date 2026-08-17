package base.api.feature.auth.service.impl;

import base.api.feature.auth.dto.request.UpdateProfileDto;
import base.api.feature.auth.repository.CriticalUserActionTokenRepository;
import base.api.feature.auth.repository.IEmailVerificationTokenRepository;
import base.api.feature.auth.repository.IPasswordResetTokenRepository;
import base.api.feature.auth.repository.IRoleRepository;
import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.posorder.repository.OrderRepository;
import base.api.feature.shift.repository.ShiftAssignmentRepository;
import base.api.feature.shiftsession.repository.ShiftSessionRepository;
import base.api.shared.config.EmailService;
import base.api.shared.config.VerificationCodeIssuer;
import base.api.shared.entity.CriticalUserActionTokenModel;
import base.api.shared.entity.EmailVerificationTokenModel;
import base.api.shared.entity.ShiftAssignmentModel;
import base.api.shared.entity.ShiftSessionModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.ShiftSessionStatus;
import base.api.shared.enums.ShiftStatus;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Extra unit tests for UserService profile, status, delete, guest, and email-verification flows.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProfileStatusTest {

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
    @Mock private OrderRepository orderRepository;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private UserService service;

    @BeforeEach
    void setApiUrl() {
        ReflectionTestUtils.setField(service, "apiBaseUrl", "http://localhost:1328");
    }

    // -------------------------------------------------------------------------
    // updateProfile
    // -------------------------------------------------------------------------

    @Test
    void updateProfileRejectsBlankEmail() {
        UserModel user = user(1L, UserRole.CUSTOMER, null);
        user.setEmail("old@chainstore.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UpdateProfileDto dto = profileDto("  ", "A", "B");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> service.updateProfile(1L, dto));

        assertTrue(error.getMessage().contains("Email không được để trống"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfileRejectsDuplicateEmail() {
        UserModel user = user(1L, UserRole.CUSTOMER, null);
        user.setEmail("old@chainstore.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("new@chainstore.com")).thenReturn(true);

        RuntimeException error = assertThrows(
                RuntimeException.class, () -> service.updateProfile(1L, profileDto("new@chainstore.com", "A", "B")));

        assertTrue(error.getMessage().contains("Email đã được sử dụng bởi tài khoản khác"));
    }

    @Test
    void updateProfileSucceedsWhenEmailAvailable() {
        UserModel user = user(1L, UserRole.CUSTOMER, null);
        user.setEmail("old@chainstore.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("new@chainstore.com")).thenReturn(false);
        when(userRepository.save(any(UserModel.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileDto dto = profileDto("New@ChainStore.com", "Lan", "Nguyen");
        dto.setPhone("0912 345 678");
        dto.setGender("FEMALE");

        UserModel saved = service.updateProfile(1L, dto);

        assertEquals("new@chainstore.com", saved.getEmail());
        assertEquals("Lan", saved.getFirstName());
        assertEquals("Nguyen", saved.getLastName());
        assertEquals("0912345678", saved.getPhone());
        verify(userRepository).save(user);
    }

    @Test
    void updateProfileRejectsUnknownUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException error = assertThrows(
                RuntimeException.class, () -> service.updateProfile(99L, profileDto("a@b.com", "A", "B")));

        assertTrue(error.getMessage().contains("Không tìm thấy người dùng"));
    }

    // -------------------------------------------------------------------------
    // updateUserStatus
    // -------------------------------------------------------------------------

    @Test
    void updateUserStatusRejectsSelfOperation() {
        UserModel actor = user(1L, UserRole.ADMIN, null);
        actor.setEmail("admin@chainstore.com");

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.updateUserStatus(1L, false, actor));

        assertTrue(error.getMessage().contains("Không thể thao tác trên tài khoản của chính mình."));
    }

    @Test
    void updateUserStatusRejectsActorWithoutManagePermission() {
        UserModel actor = user(1L, UserRole.CASHIER, 10L);
        UserModel target = user(2L, UserRole.CASHIER, 10L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        ForbiddenException error = assertThrows(
                ForbiddenException.class, () -> service.updateUserStatus(2L, false, actor));

        assertTrue(error.getMessage().contains("Không có quyền quản lý user"));
    }

    @Test
    void updateUserStatusRejectsBranchManagerManagingNonBranchStaff() {
        UserModel actor = user(1L, UserRole.BRANCH_MANAGER, 10L);
        UserModel target = user(2L, UserRole.DIRECTOR, null);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        ForbiddenException error = assertThrows(
                ForbiddenException.class, () -> service.updateUserStatus(2L, false, actor));

        assertTrue(error.getMessage().contains("Branch manager chỉ được quản lý Cashier và Inventory staff."));
    }

    @Test
    void updateUserStatusRejectsBranchManagerOtherBranch() {
        UserModel actor = user(1L, UserRole.BRANCH_MANAGER, 10L);
        UserModel target = user(2L, UserRole.CASHIER, 20L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        ForbiddenException error = assertThrows(
                ForbiddenException.class, () -> service.updateUserStatus(2L, false, actor));

        assertTrue(error.getMessage().contains("Chỉ được quản lý nhân viên tại chi nhánh của mình."));
    }

    @Test
    void updateUserStatusDeactivatesNonCriticalCashier() {
        UserModel actor = user(1L, UserRole.ADMIN, null);
        UserModel target = user(2L, UserRole.CASHIER, 10L);
        target.setActive(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(UserModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shiftSessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(2L), anyList()))
                .thenReturn(Optional.empty());
        when(shiftAssignmentRepository.findPublishedAssignmentsFrom(eq(2L), any(LocalDateTime.class), eq(ShiftStatus.PUBLISHED)))
                .thenReturn(List.of());

        UserModel saved = service.updateUserStatus(2L, false, actor);

        assertFalse(saved.isActive());
        verify(userRepository).save(target);
    }

    @Test
    void updateUserStatusActivatesNonCriticalCashier() {
        UserModel actor = user(1L, UserRole.ADMIN, null);
        UserModel target = user(2L, UserRole.CASHIER, 10L);
        target.setActive(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(UserModel.class))).thenAnswer(inv -> inv.getArgument(0));

        UserModel saved = service.updateUserStatus(2L, true, actor);

        assertTrue(saved.isActive());
    }

    // -------------------------------------------------------------------------
    // deleteUser
    // -------------------------------------------------------------------------

    @Test
    void deleteUserRejectsSelfDelete() {
        UserModel actor = user(1L, UserRole.ADMIN, null);

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.deleteUser(1L, actor));

        assertTrue(error.getMessage().contains("Không thể thao tác trên tài khoản của chính mình."));
    }

    @Test
    void deleteUserBlocksWhenOpenShiftSessionExists() {
        UserModel actor = user(1L, UserRole.ADMIN, null);
        UserModel target = user(2L, UserRole.CASHIER, 10L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(shiftSessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(2L), anyList()))
                .thenReturn(Optional.of(new ShiftSessionModel()));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.deleteUser(2L, actor));

        assertTrue(error.getMessage().contains(
                "Cannot delete this account while a shift session is still open. Deactivate the account first."));
        verify(userRepository, never()).delete(any(UserModel.class));
    }

    @Test
    void deleteUserBlocksWhenPublishedAssignmentExists() {
        UserModel actor = user(1L, UserRole.ADMIN, null);
        UserModel target = user(2L, UserRole.CASHIER, 10L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(shiftSessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(2L), anyList()))
                .thenReturn(Optional.empty());
        when(shiftAssignmentRepository.findPublishedAssignmentsFrom(eq(2L), any(LocalDateTime.class), eq(ShiftStatus.PUBLISHED)))
                .thenReturn(List.of(new ShiftAssignmentModel()));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.deleteUser(2L, actor));

        assertTrue(error.getMessage().contains(
                "Cannot delete this account while they are assigned to published shifts. Deactivate the account first (assignments will be cleared)."));
        verify(userRepository, never()).delete(any(UserModel.class));
    }

    @Test
    void deleteUserBlocksWhenSalesHistoryExists() {
        UserModel actor = user(1L, UserRole.ADMIN, null);
        UserModel target = user(2L, UserRole.CASHIER, 10L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(shiftSessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(2L), anyList()))
                .thenReturn(Optional.empty());
        when(shiftAssignmentRepository.findPublishedAssignmentsFrom(eq(2L), any(LocalDateTime.class), eq(ShiftStatus.PUBLISHED)))
                .thenReturn(List.of());
        when(orderRepository.existsByCashierId(2L)).thenReturn(true);

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.deleteUser(2L, actor));

        assertTrue(error.getMessage().contains("đã có lịch sử bán hàng"));
        verify(userRepository, never()).delete(any(UserModel.class));
    }

    @Test
    void deleteUserSucceedsForNonCriticalWithoutBlocks() {
        UserModel actor = user(1L, UserRole.ADMIN, null);
        UserModel target = user(2L, UserRole.CASHIER, 10L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(shiftSessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(2L), anyList()))
                .thenReturn(Optional.empty());
        when(shiftAssignmentRepository.findPublishedAssignmentsFrom(eq(2L), any(LocalDateTime.class), eq(ShiftStatus.PUBLISHED)))
                .thenReturn(List.of());

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString(), anyString()))
                .thenReturn(1);

        service.deleteUser(2L, actor);

        InOrder inOrder = inOrder(jdbcTemplate, userRepository);
        inOrder.verify(jdbcTemplate)
                .update("DELETE FROM password_reset_tokens WHERE user_id = ?", 2L);
        inOrder.verify(jdbcTemplate)
                .update("DELETE FROM email_verification_tokens WHERE user_id = ?", 2L);
        inOrder.verify(userRepository).delete(target);
        inOrder.verify(userRepository).flush();
    }

    @Test
    void deleteUserSkipsTokenTablesMissingFromSchema() {
        UserModel actor = user(1L, UserRole.ADMIN, null);
        UserModel target = user(2L, UserRole.CASHIER, 10L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(shiftSessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(2L), anyList()))
                .thenReturn(Optional.empty());
        when(shiftAssignmentRepository.findPublishedAssignmentsFrom(eq(2L), any(LocalDateTime.class), eq(ShiftStatus.PUBLISHED)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString(), anyString()))
                .thenAnswer(inv -> "password_reset_tokens".equals(inv.getArgument(2)) ? 1 : 0);

        service.deleteUser(2L, actor);

        verify(jdbcTemplate).update("DELETE FROM password_reset_tokens WHERE user_id = ?", 2L);
        verify(jdbcTemplate, never())
                .update("DELETE FROM email_verification_tokens WHERE user_id = ?", 2L);
        verify(userRepository).delete(target);
        verify(userRepository).flush();
    }

    @Test
    void deleteUserTurnsRemainingForeignKeyFailureIntoDeactivateMessage() {
        UserModel actor = user(1L, UserRole.ADMIN, null);
        UserModel target = user(2L, UserRole.CASHIER, 10L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(shiftSessionRepository.findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(eq(2L), anyList()))
                .thenReturn(Optional.empty());
        when(shiftAssignmentRepository.findPublishedAssignmentsFrom(eq(2L), any(LocalDateTime.class), eq(ShiftStatus.PUBLISHED)))
                .thenReturn(List.of());
        doThrow(new DataIntegrityViolationException("fk shifts"))
                .when(userRepository).flush();

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.deleteUser(2L, actor));

        assertTrue(error.getMessage().contains("Vui lòng vô hiệu hóa tài khoản"));
        verify(userRepository).delete(target);
    }

    // -------------------------------------------------------------------------
    // sendCriticalUserActionCode
    // -------------------------------------------------------------------------

    @Test
    void sendCriticalUserActionCodeRejectsNonCriticalTarget() {
        UserModel actor = user(1L, UserRole.ADMIN, null);
        actor.setEmail("admin@chainstore.com");
        UserModel target = user(2L, UserRole.CASHIER, 10L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.sendCriticalUserActionCode(2L, "admin@chainstore.com", "DELETE", actor));

        assertTrue(error.getMessage().contains("This account does not require verification."));
    }

    @Test
    void sendCriticalUserActionCodeRejectsEmailMismatch() {
        UserModel actor = user(1L, UserRole.ADMIN, null);
        actor.setEmail("admin@chainstore.com");
        UserModel target = user(2L, UserRole.DIRECTOR, null);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.sendCriticalUserActionCode(2L, "other@chainstore.com", "DELETE", actor));

        assertTrue(error.getMessage().contains("Email does not match your account."));
    }

    @Test
    void sendCriticalUserActionCodeSendsEmailForDirectorTarget() throws Exception {
        UserModel actor = user(1L, UserRole.ADMIN, null);
        actor.setEmail("admin@chainstore.com");
        actor.setFirstName("Admin");
        UserModel target = user(2L, UserRole.DIRECTOR, null);
        target.setFirstName("Dir");
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(verificationCodeIssuer.issueCode()).thenReturn("123456");
        when(criticalUserActionTokenRepository.save(any(CriticalUserActionTokenModel.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.sendCriticalUserActionCode(2L, "admin@chainstore.com", "DELETE", actor);

        verify(criticalUserActionTokenRepository).save(any(CriticalUserActionTokenModel.class));
        verify(emailService).sendHtmlEmail(eq("admin@chainstore.com"), anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // getOrCreateGuestByPhone
    // -------------------------------------------------------------------------

    @Test
    void getOrCreateGuestByPhoneRejectsBlankPhone() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> service.getOrCreateGuestByPhone("  ", "Guest"));

        assertTrue(error.getMessage().contains("Số điện thoại không được để trống"));
    }

    @Test
    void getOrCreateGuestByPhoneCreatesNewGuest() {
        when(userRepository.findByPhone("0912345678")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(UserModel.class))).thenAnswer(inv -> {
            UserModel u = inv.getArgument(0);
            u.setId(77L);
            return u;
        });

        UserModel guest = service.getOrCreateGuestByPhone("0912 345 678", "Walk-in");

        assertEquals(77L, guest.getId());
        assertEquals("0912345678", guest.getPhone());
        assertEquals("walkin_0912345678@guest.chainstore.com", guest.getEmail());
        assertEquals(UserRole.CUSTOMER, guest.getRole());
        assertTrue(guest.isVerified());
        assertTrue(guest.isActive());
    }

    @Test
    void getOrCreateGuestByPhoneReturnsExisting() {
        UserModel existing = user(50L, UserRole.CUSTOMER, null);
        existing.setPhone("0912345678");
        when(userRepository.findByPhone("0912345678")).thenReturn(Optional.of(existing));

        UserModel guest = service.getOrCreateGuestByPhone("0912345678", "Anyone");

        assertEquals(50L, guest.getId());
        verify(userRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // resendVerificationEmail
    // -------------------------------------------------------------------------

    @Test
    void resendVerificationEmailRejectsBlankContact() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> service.resendVerificationEmail("  "));

        assertTrue(error.getMessage().contains("Vui lòng nhập email hoặc tên đăng nhập"));
    }

    @Test
    void resendVerificationEmailRejectsUnknownAccount() {
        when(userRepository.findByEmail("missing@chainstore.com")).thenReturn(Optional.empty());

        Exception error = assertThrows(
                Exception.class, () -> service.resendVerificationEmail("missing@chainstore.com"));

        assertTrue(error.getMessage().contains("Không tìm thấy tài khoản với thông tin này"));
    }

    @Test
    void resendVerificationEmailRejectsAlreadyVerified() {
        UserModel user = user(1L, UserRole.CUSTOMER, null);
        user.setEmail("user@chainstore.com");
        user.setVerified(true);
        when(userRepository.findByEmail("user@chainstore.com")).thenReturn(Optional.of(user));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> service.resendVerificationEmail("user@chainstore.com"));

        assertTrue(error.getMessage().contains("Tài khoản đã được xác thực, không cần gửi lại email"));
    }

    @Test
    void resendVerificationEmailSucceedsForUnverifiedUser() throws Exception {
        UserModel user = user(1L, UserRole.CUSTOMER, null);
        user.setEmail("user@chainstore.com");
        user.setUserName("user01");
        user.setVerified(false);
        when(userRepository.findByEmail("user@chainstore.com")).thenReturn(Optional.of(user));
        when(verificationCodeIssuer.issueCode()).thenReturn("123456");
        when(emailVerificationTokenRepository.save(any(EmailVerificationTokenModel.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.resendVerificationEmail("user@chainstore.com");

        verify(emailVerificationTokenRepository).deleteByEmail("user@chainstore.com");
        verify(emailVerificationTokenRepository).save(any(EmailVerificationTokenModel.class));
        verify(emailService).sendHtmlEmail(eq("user@chainstore.com"), anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // verifyEmailByToken
    // -------------------------------------------------------------------------

    @Test
    void verifyEmailByTokenRejectsInvalidToken() {
        when(emailVerificationTokenRepository.findByVerificationToken("bad")).thenReturn(Optional.empty());

        Exception error = assertThrows(Exception.class, () -> service.verifyEmailByToken("bad"));

        assertTrue(error.getMessage().contains("Token không hợp lệ"));
    }

    @Test
    void verifyEmailByTokenRejectsUsedToken() {
        EmailVerificationTokenModel token = token("tok", false);
        token.setUsed(true);
        when(emailVerificationTokenRepository.findByVerificationToken("tok")).thenReturn(Optional.of(token));

        Exception error = assertThrows(Exception.class, () -> service.verifyEmailByToken("tok"));

        assertTrue(error.getMessage().contains("Token đã được sử dụng"));
    }

    @Test
    void verifyEmailByTokenRejectsExpiredToken() {
        EmailVerificationTokenModel token = token("tok", true);
        when(emailVerificationTokenRepository.findByVerificationToken("tok")).thenReturn(Optional.of(token));

        Exception error = assertThrows(Exception.class, () -> service.verifyEmailByToken("tok"));

        assertTrue(error.getMessage().contains("Token đã hết hạn"));
    }

    @Test
    void verifyEmailByTokenRejectsMissingUser() {
        EmailVerificationTokenModel token = token("tok", false);
        token.setUserId(99L);
        when(emailVerificationTokenRepository.findByVerificationToken("tok")).thenReturn(Optional.of(token));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        Exception error = assertThrows(Exception.class, () -> service.verifyEmailByToken("tok"));

        assertTrue(error.getMessage().contains("Không tìm thấy người dùng"));
    }

    @Test
    void verifyEmailByTokenSucceedsAndMarksUsed() throws Exception {
        EmailVerificationTokenModel token = token("tok", false);
        token.setUserId(3L);
        UserModel user = user(3L, UserRole.CUSTOMER, null);
        user.setEmail("user@chainstore.com");
        user.setUserName("user01");
        user.setVerified(false);
        when(emailVerificationTokenRepository.findByVerificationToken("tok")).thenReturn(Optional.of(token));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(emailVerificationTokenRepository.save(any(EmailVerificationTokenModel.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.verifyEmailByToken("tok");

        assertTrue(user.isVerified());
        assertTrue(token.isUsed());
        verify(userRepository).save(user);
        verify(emailVerificationTokenRepository).save(token);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static UpdateProfileDto profileDto(String email, String first, String last) {
        UpdateProfileDto dto = new UpdateProfileDto();
        dto.setEmail(email);
        dto.setFirstName(first);
        dto.setLastName(last);
        return dto;
    }

    private static UserModel user(Long id, UserRole role, Long branchId) {
        UserModel user = new UserModel();
        user.setId(id);
        user.setRole(role);
        user.setBranchId(branchId);
        user.setUserName("user" + id);
        user.setEmail("user" + id + "@chainstore.com");
        return user;
    }

    private static EmailVerificationTokenModel token(String value, boolean expired) {
        EmailVerificationTokenModel token = new EmailVerificationTokenModel();
        token.setVerificationToken(value);
        token.setVerificationCode("123456");
        token.setEmail("user@chainstore.com");
        token.setUserId(1L);
        token.setUsed(false);
        token.setExpiresAt(expired
                ? LocalDateTime.now().minusHours(1)
                : LocalDateTime.now().plusHours(24));
        return token;
    }
}
