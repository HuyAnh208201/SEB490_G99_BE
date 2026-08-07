package base.api.feature.branch.service.impl;

import base.api.feature.auth.repository.IRoleRepository;
import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.dto.request.CreateBranchManagerRequest;
import base.api.feature.branch.dto.request.CreateBranchRequest;
import base.api.feature.branch.dto.request.CreateCashierRequest;
import base.api.feature.branch.dto.request.UpdateBranchRequest;
import base.api.feature.branch.dto.request.UpdateBranchStatusRequest;
import base.api.feature.branch.dto.response.BranchResponse;
import base.api.feature.branch.mapper.BranchMapper;
import base.api.feature.branch.repository.BranchSuspendTokenRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.shared.config.EmailService;
import base.api.shared.config.VerificationCodeIssuer;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ConflictException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BranchServiceImpl} create / update / staff / suspend paths.
 */
@ExtendWith(MockitoExtension.class)
class BranchServiceImplTest {

    @Mock private IBranchRepository branchRepository;
    @Mock private IUserRepository userRepository;
    @Mock private IRoleRepository roleRepository;
    @Mock private BranchMapper branchMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private BranchSuspendTokenRepository branchSuspendTokenRepository;
    @Mock private EmailService emailService;
    @Mock private VerificationCodeIssuer verificationCodeIssuer;

    @InjectMocks
    private BranchServiceImpl service;

    @Test
    void createBranchSavesActiveBranch() {
        asAdmin();
        CreateBranchRequest request = createBranchRequest("  District 1  ", "  12 Main  ", "0912345678", "08:00-22:00");
        when(branchRepository.existsByNameIgnoreCase("District 1")).thenReturn(false);
        when(branchRepository.save(any(BranchModel.class))).thenAnswer(inv -> {
            BranchModel saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        BranchResponse mapped = new BranchResponse();
        mapped.setId(1L);
        when(branchMapper.toDetailResponse(any(BranchModel.class), any())).thenReturn(mapped);

        BranchResponse response = service.createBranch(request);

        assertEquals(1L, response.getId());
        ArgumentCaptor<BranchModel> captor = ArgumentCaptor.forClass(BranchModel.class);
        verify(branchRepository).save(captor.capture());
        assertEquals("District 1", captor.getValue().getName());
        assertEquals("ACTIVE", captor.getValue().getStatus());
        assertNull(captor.getValue().getManagerId());
    }

    @Test
    void createBranchRejectsBlankName() {
        asAdmin();
        CreateBranchRequest request = createBranchRequest("  ", "12 Main", "0912345678", "08:00-22:00");

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.createBranch(request));

        assertEquals("Branch name is required.", error.getMessage());
        verify(branchRepository, never()).save(any());
    }

    @Test
    void createBranchRejectsDuplicateName() {
        asAdmin();
        when(branchRepository.existsByNameIgnoreCase("District 1")).thenReturn(true);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.createBranch(createBranchRequest("District 1", "12 Main", "0912345678", "08:00-22:00")));

        assertEquals("Branch already exists.", error.getMessage());
    }

    @Test
    void createBranchRejectsInvalidPhone() {
        asAdmin();

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.createBranch(createBranchRequest("District 1", "12 Main", "12345", "08:00-22:00")));

        assertEquals("Invalid phone number.", error.getMessage());
    }

    @Test
    void createBranchRejectsNonAdminOrDirector() {
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.BRANCH_MANAGER);

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.createBranch(createBranchRequest("District 1", "12 Main", "0912345678", "08:00-22:00")));

        assertEquals("Access denied.", error.getMessage());
    }

    @Test
    void updateBranchRejectsMissingBranch() {
        asAdmin();
        when(branchRepository.findById(99L)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(
                NotFoundException.class, () -> service.updateBranch(99L, updateBranchRequest()));

        assertEquals("Branch not found.", error.getMessage());
    }

    @Test
    void updateBranchRejectsInvalidStatus() {
        asAdmin();
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch(1L)));
        UpdateBranchRequest request = updateBranchRequest();
        request.setStatus("CLOSED");

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.updateBranch(1L, request));

        assertEquals("Invalid branch status.", error.getMessage());
    }

    @Test
    void suspendBranchRequiresEmailWhenDeactivating() {
        asAdmin();
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch(1L)));
        UpdateBranchStatusRequest request = new UpdateBranchStatusRequest();
        request.setStatus("SUSPENDED");
        request.setEmail(null);
        request.setVerificationCode("123456");

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.suspendBranch(1L, request));

        assertEquals("Email confirmation is required to deactivate a branch.", error.getMessage());
    }

    @Test
    void suspendBranchRequiresVerificationCode() {
        asAdmin();
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch(1L)));
        UpdateBranchStatusRequest request = new UpdateBranchStatusRequest();
        request.setStatus("SUSPENDED");
        request.setEmail("admin@example.com");
        request.setVerificationCode("  ");

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.suspendBranch(1L, request));

        assertEquals("Verification code is required to deactivate a branch.", error.getMessage());
    }

    @Test
    void createBranchManagerRejectsBranchIdMismatch() {
        asAdmin();
        CreateBranchManagerRequest request = managerRequest(2L);

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.createBranchManager(1L, request));

        assertEquals("Branch ID mismatch.", error.getMessage());
    }

    @Test
    void createBranchManagerRejectsExistingManager() {
        asAdmin();
        BranchModel existing = branch(1L);
        existing.setManagerId(9L);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(existing));

        ConflictException error = assertThrows(
                ConflictException.class, () -> service.createBranchManager(1L, managerRequest(1L)));

        assertEquals("Branch already has a manager.", error.getMessage());
    }

    @Test
    void createCashierAsAdminRequiresBranch() {
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(adminUser());
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.ADMIN);
        CreateCashierRequest request = new CreateCashierRequest();
        request.setFullName("Cashier One");
        request.setEmail("cashier@example.com");
        request.setPhone("0912345678");
        request.setPassword("Secret1!");
        request.setConfirmPassword("Secret1!");
        request.setBranchId(null);

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.createCashier(request));

        assertEquals("Branch is required.", error.getMessage());
    }

    @Test
    void getAllBranchesRejectsUnauthorizedRole() {
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.CASHIER);

        ForbiddenException error = assertThrows(ForbiddenException.class, () -> service.getAllBranches());

        assertEquals("You do not have permission to list branches.", error.getMessage());
    }

    private void asAdmin() {
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.ADMIN);
    }

    private static UserModel adminUser() {
        UserModel user = new UserModel();
        user.setId(1L);
        user.setEmail("admin@example.com");
        user.setRole(UserRole.ADMIN);
        return user;
    }

    private static CreateBranchRequest createBranchRequest(
            String name, String address, String phone, String hours) {
        CreateBranchRequest request = new CreateBranchRequest();
        request.setName(name);
        request.setAddress(address);
        request.setPhone(phone);
        request.setOperatingHours(hours);
        return request;
    }

    private static UpdateBranchRequest updateBranchRequest() {
        UpdateBranchRequest request = new UpdateBranchRequest();
        request.setName("District 1");
        request.setAddress("12 Main");
        request.setPhone("0912345678");
        request.setOperatingHours("08:00-22:00");
        request.setStatus("ACTIVE");
        return request;
    }

    private static CreateBranchManagerRequest managerRequest(Long branchId) {
        CreateBranchManagerRequest request = new CreateBranchManagerRequest();
        request.setBranchId(branchId);
        request.setFullName("Manager One");
        request.setEmail("manager@example.com");
        request.setPhone("0912345678");
        request.setPassword("Secret1!");
        request.setConfirmPassword("Secret1!");
        return request;
    }

    private static BranchModel branch(Long id) {
        BranchModel branch = new BranchModel();
        branch.setId(id);
        branch.setName("Branch " + id);
        branch.setStatus("ACTIVE");
        return branch;
    }
}
