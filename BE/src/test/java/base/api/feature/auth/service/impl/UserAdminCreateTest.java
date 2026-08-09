package base.api.feature.auth.service.impl;

import base.api.feature.auth.dto.request.CreateUserByAdminDto;
import base.api.feature.auth.repository.CriticalUserActionTokenRepository;
import base.api.feature.auth.repository.IEmailVerificationTokenRepository;
import base.api.feature.auth.repository.IPasswordResetTokenRepository;
import base.api.feature.auth.repository.IRoleRepository;
import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.shift.repository.ShiftAssignmentRepository;
import base.api.feature.shiftsession.repository.ShiftSessionRepository;
import base.api.shared.config.EmailService;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.RoleModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ConflictException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserService#createUserByAdmin} — uniqueness, role/branch rules, BM scope.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAdminCreateTest {

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
    void setApiUrl() {
        ReflectionTestUtils.setField(service, "apiBaseUrl", "http://localhost:1328");
    }

    @Test
    void createUserByAdminCreatesCashierForAdmin() throws Exception {
        UserModel creator = actor(UserRole.ADMIN, null);
        CreateUserByAdminDto dto = cashierDto(10L);
        RoleModel role = role("CASHIER");
        BranchModel branch = branch(10L, null);

        when(userRepository.existsByUserName("cashier01")).thenReturn(false);
        when(userRepository.existsByEmail("cashier01@chainstore.com")).thenReturn(false);
        when(userRepository.existsByPhone("0912345678")).thenReturn(false);
        when(roleRepository.findByName("CASHIER")).thenReturn(Optional.of(role));
        when(branchRepository.findById(10L)).thenReturn(Optional.of(branch));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-temp");
        when(userRepository.save(any(UserModel.class))).thenAnswer(inv -> {
            UserModel u = inv.getArgument(0);
            u.setId(55L);
            return u;
        });

        UserModel saved = service.createUserByAdmin(dto, creator);

        assertEquals(55L, saved.getId());
        assertTrue(saved.isVerified());
        assertTrue(saved.isActive());
        verify(emailService).sendHtmlEmail(eq("cashier01@chainstore.com"), anyString(), anyString());
    }

    @Test
    void createUserByAdminRejectsNullCreator() {
        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.createUserByAdmin(cashierDto(10L), null));

        assertTrue(error.getMessage().contains("Không xác định được người tạo"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUserByAdminRejectsDuplicateUsername() {
        when(userRepository.existsByUserName("cashier01")).thenReturn(true);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.createUserByAdmin(cashierDto(10L), actor(UserRole.ADMIN, null)));

        assertTrue(error.getMessage().contains("Username đã tồn tại"));
    }

    @Test
    void createUserByAdminRejectsDuplicateEmail() {
        when(userRepository.existsByUserName("cashier01")).thenReturn(false);
        when(userRepository.existsByEmail("cashier01@chainstore.com")).thenReturn(true);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.createUserByAdmin(cashierDto(10L), actor(UserRole.ADMIN, null)));

        assertTrue(error.getMessage().contains("Email đã được sử dụng"));
    }

    @Test
    void createUserByAdminRejectsDuplicatePhone() {
        when(userRepository.existsByUserName("cashier01")).thenReturn(false);
        when(userRepository.existsByEmail("cashier01@chainstore.com")).thenReturn(false);
        when(userRepository.existsByPhone("0912345678")).thenReturn(true);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.createUserByAdmin(cashierDto(10L), actor(UserRole.ADMIN, null)));

        assertTrue(error.getMessage().contains("Số điện thoại đã được sử dụng"));
    }

    @Test
    void createUserByAdminRejectsMissingRole() {
        when(userRepository.existsByUserName("cashier01")).thenReturn(false);
        when(userRepository.existsByEmail("cashier01@chainstore.com")).thenReturn(false);
        when(userRepository.existsByPhone("0912345678")).thenReturn(false);
        when(roleRepository.findByName("CASHIER")).thenReturn(Optional.empty());

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.createUserByAdmin(cashierDto(10L), actor(UserRole.ADMIN, null)));

        assertTrue(error.getMessage().contains("Role không tồn tại"));
    }

    @Test
    void createUserByAdminRejectsMissingBranch() {
        when(userRepository.existsByUserName("cashier01")).thenReturn(false);
        when(userRepository.existsByEmail("cashier01@chainstore.com")).thenReturn(false);
        when(userRepository.existsByPhone("0912345678")).thenReturn(false);
        when(roleRepository.findByName("CASHIER")).thenReturn(Optional.of(role("CASHIER")));
        when(branchRepository.findById(10L)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(
                NotFoundException.class,
                () -> service.createUserByAdmin(cashierDto(10L), actor(UserRole.ADMIN, null)));

        assertTrue(error.getMessage().contains("Không tìm thấy chi nhánh"));
    }

    @Test
    void createUserByAdminRequiresBranchForCashier() {
        CreateUserByAdminDto dto = cashierDto(null);
        when(userRepository.existsByUserName("cashier01")).thenReturn(false);
        when(userRepository.existsByEmail("cashier01@chainstore.com")).thenReturn(false);
        when(userRepository.existsByPhone("0912345678")).thenReturn(false);
        when(roleRepository.findByName("CASHIER")).thenReturn(Optional.of(role("CASHIER")));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.createUserByAdmin(dto, actor(UserRole.ADMIN, null)));

        assertTrue(error.getMessage().contains("Vui lòng chọn chi nhánh"));
    }

    @Test
    void createUserByAdminRejectsBranchManagerCreatingForOtherBranch() {
        when(userRepository.existsByUserName("cashier01")).thenReturn(false);
        when(userRepository.existsByEmail("cashier01@chainstore.com")).thenReturn(false);
        when(userRepository.existsByPhone("0912345678")).thenReturn(false);
        when(roleRepository.findByName("CASHIER")).thenReturn(Optional.of(role("CASHIER")));

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.createUserByAdmin(cashierDto(99L), actor(UserRole.BRANCH_MANAGER, 10L)));

        assertTrue(error.getMessage().contains("Chỉ được tạo nhân viên cho chi nhánh của mình"));
    }

    @Test
    void createUserByAdminRejectsSecondBranchManagerOnSameBranch() {
        CreateUserByAdminDto dto = dto("bm02", "bm02@chainstore.com", "0911111111", UserRole.BRANCH_MANAGER, 10L);
        BranchModel branch = branch(10L, 7L);
        when(userRepository.existsByUserName("bm02")).thenReturn(false);
        when(userRepository.existsByEmail("bm02@chainstore.com")).thenReturn(false);
        when(userRepository.existsByPhone("0911111111")).thenReturn(false);
        when(roleRepository.findByName("BRANCH_MANAGER")).thenReturn(Optional.of(role("BRANCH_MANAGER")));
        when(branchRepository.findById(10L)).thenReturn(Optional.of(branch));

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.createUserByAdmin(dto, actor(UserRole.ADMIN, null)));

        assertTrue(error.getMessage().contains("Chi nhánh đã có quản lý"));
    }

    @Test
    void createUserByAdminRejectsSecondActiveDirectorSlot() {
        CreateUserByAdminDto dto = dto("dir2", "dir2@chainstore.com", "0922222222", UserRole.DIRECTOR, null);
        when(userRepository.existsByUserName("dir2")).thenReturn(false);
        when(userRepository.existsByEmail("dir2@chainstore.com")).thenReturn(false);
        when(userRepository.existsByPhone("0922222222")).thenReturn(false);
        when(roleRepository.findByName("DIRECTOR")).thenReturn(Optional.of(role("DIRECTOR")));
        when(userRepository.countActiveByRoleName("DIRECTOR")).thenReturn(1L);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.createUserByAdmin(dto, actor(UserRole.ADMIN, null)));

        assertTrue(error.getMessage().contains("Only one active"));
    }

    @Test
    void createUserByAdminAssignsManagerIdWhenCreatingBranchManager() throws Exception {
        CreateUserByAdminDto dto = dto("bm01", "bm01@chainstore.com", "0933333333", UserRole.BRANCH_MANAGER, 10L);
        BranchModel branch = branch(10L, null);
        when(userRepository.existsByUserName("bm01")).thenReturn(false);
        when(userRepository.existsByEmail("bm01@chainstore.com")).thenReturn(false);
        when(userRepository.existsByPhone("0933333333")).thenReturn(false);
        when(roleRepository.findByName("BRANCH_MANAGER")).thenReturn(Optional.of(role("BRANCH_MANAGER")));
        when(branchRepository.findById(10L)).thenReturn(Optional.of(branch));
        when(userRepository.countActiveByRoleName(anyString())).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-temp");
        when(userRepository.save(any(UserModel.class))).thenAnswer(inv -> {
            UserModel u = inv.getArgument(0);
            u.setId(88L);
            return u;
        });

        service.createUserByAdmin(dto, actor(UserRole.ADMIN, null));

        ArgumentCaptor<BranchModel> branchCaptor = ArgumentCaptor.forClass(BranchModel.class);
        verify(branchRepository).save(branchCaptor.capture());
        assertEquals(88L, branchCaptor.getValue().getManagerId());
    }

    private static CreateUserByAdminDto cashierDto(Long branchId) {
        return dto("cashier01", "cashier01@chainstore.com", "0912345678", UserRole.CASHIER, branchId);
    }

    private static CreateUserByAdminDto dto(
            String userName, String email, String phone, UserRole role, Long branchId) {
        CreateUserByAdminDto dto = new CreateUserByAdminDto();
        dto.setUserName(userName);
        dto.setEmail(email);
        dto.setFirstName("First");
        dto.setLastName("Last");
        dto.setPhone(phone);
        dto.setRole(role);
        dto.setBranchId(branchId);
        return dto;
    }

    private static UserModel actor(UserRole role, Long branchId) {
        UserModel user = new UserModel();
        user.setId(1L);
        user.setRole(role);
        user.setBranchId(branchId);
        return user;
    }

    private static RoleModel role(String name) {
        RoleModel role = new RoleModel();
        role.setId(1L);
        role.setName(name);
        return role;
    }

    private static BranchModel branch(Long id, Long managerId) {
        BranchModel branch = new BranchModel();
        branch.setId(id);
        branch.setManagerId(managerId);
        return branch;
    }
}
