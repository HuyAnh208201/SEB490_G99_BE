package base.api.feature.branch.service.impl;

import base.api.feature.auth.repository.IRoleRepository;
import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.dto.request.CreateBranchManagerRequest;
import base.api.feature.branch.dto.request.CreateBranchRequest;
import base.api.feature.branch.dto.request.CreateCashierRequest;
import base.api.feature.branch.dto.request.CreateInventoryStaffRequest;
import base.api.feature.branch.dto.request.UpdateBranchRequest;
import base.api.feature.branch.dto.request.UpdateBranchStatusRequest;
import base.api.feature.branch.dto.response.BranchResponse;
import base.api.feature.branch.dto.response.UserResponse;
import base.api.feature.branch.mapper.BranchMapper;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.branch.service.IBranchService;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.RoleModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ConflictException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class BranchServiceImpl implements IBranchService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^0[0-9]{9}$");
    private static final Set<String> ALLOWED_BRANCH_STATUSES = Set.of("ACTIVE", "SUSPENDED");

    @Autowired
    private IBranchRepository branchRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IRoleRepository roleRepository;

    @Autowired
    private BranchMapper branchMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public BranchResponse createBranch(CreateBranchRequest request) {
        assertAdmin();

        String normalizedName = normalizeBranchName(request.getName());
        String normalizedAddress = normalizeRequiredText(request.getAddress(), "Address is required.");
        String normalizedPhone = normalizePhone(request.getPhone());
        String normalizedOperatingHours = normalizeRequiredText(
                request.getOperatingHours(), "Operating hours is required.");

        validateDuplicateBranchName(normalizedName, null);

        BranchModel branch = new BranchModel();
        branch.setName(normalizedName);
        branch.setAddress(normalizedAddress);
        branch.setPhone(normalizedPhone);
        branch.setOperatingHours(normalizedOperatingHours);
        branch.setStatus("ACTIVE");
        branch.setManagerId(null);

        return branchMapper.toDetailResponse(branchRepository.save(branch), null);
    }

    @Override
    @Transactional
    public BranchResponse updateBranch(Long id, UpdateBranchRequest request) {
        assertAdmin();

        BranchModel branch = findBranchOrThrow(id);

        String normalizedName = normalizeBranchName(request.getName());
        String normalizedAddress = normalizeRequiredText(request.getAddress(), "Address is required.");
        String normalizedPhone = normalizePhone(request.getPhone());
        String normalizedOperatingHours = normalizeRequiredText(
                request.getOperatingHours(), "Operating hours is required.");
        String normalizedStatus = normalizeBranchStatus(request.getStatus());

        validateDuplicateBranchName(normalizedName, id);

        branch.setName(normalizedName);
        branch.setAddress(normalizedAddress);
        branch.setPhone(normalizedPhone);
        branch.setOperatingHours(normalizedOperatingHours);
        branch.setStatus(normalizedStatus);

        return branchMapper.toDetailResponse(
                branchRepository.save(branch),
                resolveManagerName(branch.getManagerId()));
    }

    @Override
    public BranchResponse getBranch(Long id) {
        BranchModel branch = findBranchOrThrow(id);
        assertCanViewBranch(branch.getId());
        return branchMapper.toDetailResponse(branch, resolveManagerName(branch.getManagerId()));
    }

    @Override
    public List<BranchResponse> getAllBranches() {
        assertAdminOrDirector();

        return branchRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(branch -> branchMapper.toListResponse(branch, resolveManagerName(branch.getManagerId())))
                .toList();
    }

    @Override
    @Transactional
    public BranchResponse suspendBranch(Long id, UpdateBranchStatusRequest request) {
        assertAdmin();

        BranchModel branch = findBranchOrThrow(id);
        String normalizedStatus = normalizeBranchStatus(request.getStatus());

        branch.setStatus(normalizedStatus);

        return branchMapper.toDetailResponse(
                branchRepository.save(branch),
                resolveManagerName(branch.getManagerId()));
    }

    @Override
    @Transactional
    public UserResponse createBranchManager(Long branchId, CreateBranchManagerRequest request) {
        assertAdmin();

        if (!branchId.equals(request.getBranchId())) {
            throw new BadRequestException("Branch ID mismatch.");
        }

        BranchModel branch = findBranchOrThrow(branchId);
        if (branch.getManagerId() != null) {
            throw new ConflictException("Branch already has a manager.");
        }

        validatePasswordConfirmation(request.getPassword(), request.getConfirmPassword());

        String normalizedFullName = normalizeFullName(request.getFullName());
        String normalizedEmail = normalizeEmail(request.getEmail());
        String normalizedPhone = normalizePhone(request.getPhone());

        validateUniqueEmail(normalizedEmail);
        validateUniquePhone(normalizedPhone);

        RoleModel role = findRoleOrThrow(UserRole.BRANCH_MANAGER);

        UserModel user = buildUser(
                normalizedFullName,
                normalizedEmail,
                normalizedPhone,
                request.getPassword(),
                role,
                branchId);

        UserModel savedUser = userRepository.save(user);

        branch.setManagerId(savedUser.getId());
        branchRepository.save(branch);

        return branchMapper.toUserResponse(savedUser);
    }

    @Override
    @Transactional
    public UserResponse createInventoryStaff(CreateInventoryStaffRequest request) {
        return createStaff(
                request.getFullName(),
                request.getEmail(),
                request.getPhone(),
                request.getPassword(),
                request.getConfirmPassword(),
                request.getBranchId(),
                UserRole.INVENTORY_STAFF);
    }

    @Override
    @Transactional
    public UserResponse createCashier(CreateCashierRequest request) {
        return createStaff(
                request.getFullName(),
                request.getEmail(),
                request.getPhone(),
                request.getPassword(),
                request.getConfirmPassword(),
                request.getBranchId(),
                UserRole.CASHIER);
    }

    private UserResponse createStaff(
            String fullName,
            String email,
            String phone,
            String password,
            String confirmPassword,
            Long branchId,
            UserRole role) {

        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole currentRole = currentUserProvider.getCurrentUserRole();

        if (currentRole != UserRole.ADMIN && currentRole != UserRole.BRANCH_MANAGER) {
            throw new ForbiddenException("Access denied.");
        }

        Long targetBranchId = resolveTargetBranchId(currentUser, currentRole, branchId);
        findBranchOrThrow(targetBranchId);

        validatePasswordConfirmation(password, confirmPassword);

        String normalizedFullName = normalizeFullName(fullName);
        String normalizedEmail = normalizeEmail(email);
        String normalizedPhone = normalizePhone(phone);

        validateUniqueEmail(normalizedEmail);
        validateUniquePhone(normalizedPhone);

        RoleModel roleEntity = findRoleOrThrow(role);

        UserModel user = buildUser(
                normalizedFullName,
                normalizedEmail,
                normalizedPhone,
                password,
                roleEntity,
                targetBranchId);

        return branchMapper.toUserResponse(userRepository.save(user));
    }

    private UserModel buildUser(
            String fullName,
            String email,
            String phone,
            String password,
            RoleModel role,
            Long branchId) {

        UserModel user = new UserModel();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(password));
        user.setRoleEntity(role);
        user.setBranchId(branchId);
        user.setStatus("active");
        return user;
    }

    private Long resolveTargetBranchId(UserModel currentUser, UserRole currentRole, Long branchId) {
        if (currentRole == UserRole.ADMIN) {
            if (branchId == null) {
                throw new BadRequestException("Branch is required.");
            }
            return branchId;
        }
        if (currentUser.getBranchId() == null) {
            throw new BadRequestException("Branch manager is not assigned to a branch.");
        }
        return currentUser.getBranchId();
    }

    private BranchModel findBranchOrThrow(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Branch not found."));
    }

    private RoleModel findRoleOrThrow(UserRole role) {
        return roleRepository.findByName(role.name())
                .orElseThrow(() -> new NotFoundException("Role not found."));
    }

    private void validateDuplicateBranchName(String name, Long currentId) {
        boolean exists = currentId == null
                ? branchRepository.existsByNameIgnoreCase(name)
                : branchRepository.existsByNameIgnoreCaseAndIdNot(name, currentId);

        if (exists) {
            throw new ConflictException("Branch already exists.");
        }
    }

    private void validateUniqueEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already exists.");
        }
    }

    private void validateUniquePhone(String phone) {
        if (userRepository.existsByPhone(phone)) {
            throw new ConflictException("Phone already exists.");
        }
    }

    private void validatePasswordConfirmation(String password, String confirmPassword) {
        if (password == null || confirmPassword == null || !password.equals(confirmPassword)) {
            throw new BadRequestException("Password confirmation mismatch.");
        }
    }

    private String normalizeBranchName(String value) {
        String normalized = normalizeWhitespace(value);
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException("Branch name is required.");
        }
        if (normalized.length() > 255) {
            throw new BadRequestException("Branch name must not exceed 255 characters.");
        }
        return normalized;
    }

    private String normalizeFullName(String value) {
        String normalized = normalizeWhitespace(value);
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException("Full name is required.");
        }
        if (normalized.length() > 255) {
            throw new BadRequestException("Full name must not exceed 255 characters.");
        }
        return normalized;
    }

    private String normalizeRequiredText(String value, String blankMessage) {
        String normalized = normalizeWhitespace(value);
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException(blankMessage);
        }
        if (normalized.length() > 255) {
            throw new BadRequestException("Value must not exceed 255 characters.");
        }
        return normalized;
    }

    private String normalizePhone(String phone) {
        String normalized = phone == null ? null : phone.trim().replaceAll("\\s+", "");
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException("Phone is required.");
        }
        if (!PHONE_PATTERN.matcher(normalized).matches()) {
            throw new BadRequestException("Invalid phone number.");
        }
        return normalized;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email is required.");
        }
        return email.trim().toLowerCase();
    }

    private String normalizeBranchStatus(String status) {
        String normalized = status == null ? null : status.trim().toUpperCase();
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException("Status is required.");
        }
        if (!ALLOWED_BRANCH_STATUSES.contains(normalized)) {
            throw new BadRequestException("Invalid branch status.");
        }
        return normalized;
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String resolveManagerName(Long managerId) {
        if (managerId == null) {
            return null;
        }
        return userRepository.findById(managerId)
                .map(UserModel::getFullName)
                .orElse(null);
    }

    private void assertAdmin() {
        UserRole role = currentUserProvider.getCurrentUserRole();
        if (role != UserRole.ADMIN) {
            throw new ForbiddenException("Access denied.");
        }
    }

    private void assertAdminOrDirector() {
        UserRole role = currentUserProvider.getCurrentUserRole();
        if (role != UserRole.ADMIN && role != UserRole.DIRECTOR) {
            throw new ForbiddenException("Access denied.");
        }
    }

    private void assertCanViewBranch(Long branchId) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole role = currentUserProvider.getCurrentUserRole();

        if (role == UserRole.ADMIN || role == UserRole.DIRECTOR) {
            return;
        }

        if (role == UserRole.BRANCH_MANAGER
                && currentUser.getBranchId() != null
                && currentUser.getBranchId().equals(branchId)) {
            return;
        }

        throw new ForbiddenException("Access denied.");
    }
}
