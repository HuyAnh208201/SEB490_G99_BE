package base.api.feature.branch.service.impl;

import base.api.feature.auth.repository.IRoleRepository;
import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.dto.request.AssignStaffRequest;
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
import base.api.feature.branch.dto.request.SendBranchSuspendCodeRequest;
import base.api.feature.branch.repository.BranchSuspendTokenRepository;
import base.api.shared.config.EmailService;
import base.api.shared.config.VerificationCodeIssuer;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.BranchSuspendTokenModel;
import base.api.shared.entity.RoleModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ConflictException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import base.api.shared.dto.PageRequestDTO;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.regex.Pattern;

@Service
@Slf4j
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

    @Autowired
    private BranchSuspendTokenRepository branchSuspendTokenRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private VerificationCodeIssuer verificationCodeIssuer;

    @Override
    @Transactional
    public BranchResponse createBranch(CreateBranchRequest request) {
        assertAdminOrDirector();

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
        assertAdminOrDirector();

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
        UserRole role = currentUserProvider.getCurrentUserRole();
        UserRole webRole = role == null ? null : role.toWebRole();

        if (webRole == UserRole.ADMIN || webRole == UserRole.DIRECTOR) {
            return branchRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                    .map(branch -> branchMapper.toListResponse(branch, resolveManagerName(branch.getManagerId())))
                    .toList();
        }

        if (webRole == UserRole.BRANCH_MANAGER) {
            UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
            if (currentUser.getBranchId() == null) {
                return List.of();
            }
            return branchRepository.findById(currentUser.getBranchId()).stream()
                    .map(branch -> branchMapper.toListResponse(branch, resolveManagerName(branch.getManagerId())))
                    .toList();
        }

        throw new ForbiddenException("You do not have permission to list branches.");
    }

    @Override
    public Page<BranchResponse> getBranchPage(PageRequestDTO pageRequest, String status) {
        PageRequestDTO query = pageRequest == null ? new PageRequestDTO() : pageRequest;
        UserRole role = currentUserProvider.getCurrentUserRole();
        UserRole webRole = role == null ? null : role.toWebRole();

        if (webRole == UserRole.BRANCH_MANAGER) {
            UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
            List<BranchResponse> content = currentUser.getBranchId() == null
                    ? List.of()
                    : branchRepository.findById(currentUser.getBranchId()).stream()
                    .filter(branch -> status == null || status.isBlank()
                            || branch.getStatus().equalsIgnoreCase(status.trim()))
                    .filter(branch -> matchesBranchSearch(branch, query.normalizedSearch()))
                    .map(branch -> branchMapper.toListResponse(branch, resolveManagerName(branch.getManagerId())))
                    .toList();
            return new PageImpl<>(content, query.toPageable(), content.size());
        }

        if (webRole != UserRole.ADMIN && webRole != UserRole.DIRECTOR) {
            throw new ForbiddenException("You do not have permission to list branches.");
        }

        Specification<BranchModel> specification = (root, ignored, cb) -> cb.conjunction();
        String search = query.normalizedSearch();
        if (search != null) {
            String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, ignored, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("address")), pattern),
                    cb.like(cb.lower(root.get("phone")), pattern),
                    cb.like(cb.lower(root.get("area")), pattern),
                    cb.like(cb.lower(root.get("route")), pattern)
            ));
        }
        if (status != null && !status.isBlank()) {
            specification = specification.and((root, ignored, cb) ->
                    cb.equal(cb.lower(root.get("status")), status.trim().toLowerCase(Locale.ROOT)));
        }
        return branchRepository.findAll(
                        specification,
                        query.toPageable("id", Sort.Direction.ASC, Set.of("id", "name", "status", "area")))
                .map(branch -> branchMapper.toListResponse(branch, resolveManagerName(branch.getManagerId())));
    }

    private boolean matchesBranchSearch(BranchModel branch, String search) {
        if (search == null) {
            return true;
        }
        String normalized = search.toLowerCase(Locale.ROOT);
        return Stream.of(branch.getName(), branch.getAddress(), branch.getPhone(), branch.getArea(), branch.getRoute())
                .filter(value -> value != null)
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(normalized));
    }

    @Override
    @Transactional
    public BranchResponse suspendBranch(Long id, UpdateBranchStatusRequest request) {
        assertAdminOrDirector();

        BranchModel branch = findBranchOrThrow(id);
        String normalizedStatus = normalizeBranchStatus(request.getStatus());

        if ("SUSPENDED".equals(normalizedStatus)) {
            verifyBranchSuspendCode(id, request.getEmail(), request.getVerificationCode());
        }

        branch.setStatus(normalizedStatus);

        return branchMapper.toDetailResponse(
                branchRepository.save(branch),
                resolveManagerName(branch.getManagerId()));
    }

    @Override
    @Transactional
    public void sendBranchSuspendCode(Long branchId, SendBranchSuspendCodeRequest request) {
        assertAdminOrDirector();

        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        BranchModel branch = findBranchOrThrow(branchId);

        String normalizedEmail = normalizeEmail(request.getEmail());
        if (!normalizedEmail.equalsIgnoreCase(currentUser.getEmail())) {
            throw new BadRequestException("Email does not match your account.");
        }

        String code = verificationCodeIssuer.issueCode();

        BranchSuspendTokenModel token = new BranchSuspendTokenModel();
        token.setBranchId(branchId);
        token.setUserId(currentUser.getId());
        token.setVerificationCode(code);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        branchSuspendTokenRepository.save(token);

        String fullName = currentUser.getFullName();
        if (fullName == null || fullName.isBlank()) {
            fullName = currentUser.getUserName() != null ? currentUser.getUserName() : normalizedEmail;
        }

        try {
            String subject = "ChainStore — Mã xác nhận vô hiệu hóa chi nhánh";
            String body = String.format(
                    "<html>" +
                            "<body style='font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>" +
                            "<div style='max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 10px;'>" +
                            "<div style='text-align: center; margin-bottom: 30px;'>" +
                            "<h1 style='color: #0f172a; margin: 0;'>ChainStore</h1>" +
                            "</div>" +
                            "<h2 style='color: #0f172a;'>Xin chào %s!</h2>" +
                            "<p>Bạn đã yêu cầu <strong>vô hiệu hóa chi nhánh</strong> " +
                            "<strong>%s</strong>. Đây là hành động quan trọng — vui lòng nhập mã bên dưới để xác nhận:</p>" +
                            "<div style='background-color: #f9f9f9; padding: 20px; border-radius: 8px; margin: 20px 0; text-align: center;'>" +
                            "<p style='margin: 0 0 8px; color: #666; font-size: 14px;'>Mã xác nhận</p>" +
                            "<p style='margin: 0; font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #0058be; font-family: monospace;'>%s</p>" +
                            "</div>" +
                            "<div style='background-color: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0;'>" +
                            "<p style='margin: 0; color: #856404;'><strong>Lưu ý:</strong> Mã có hiệu lực trong 15 phút. " +
                            "Nếu bạn không thực hiện yêu cầu này, hãy bỏ qua email.</p>" +
                            "</div>" +
                            "<hr style='border: none; border-top: 1px solid #e0e0e0; margin: 30px 0;'>" +
                            "<p style='color: #999; font-size: 12px; text-align: center;'>© ChainStore. All rights reserved.</p>" +
                            "</div>" +
                            "</body>" +
                            "</html>",
                    fullName,
                    branch.getName(),
                    code);

            emailService.sendHtmlEmail(normalizedEmail, subject, body);
        } catch (Exception ex) {
            if (verificationCodeIssuer.isMock()) {
                log.warn("SMTP failed in mock OTP mode; demo code remains valid for {}", normalizedEmail);
            } else {
                throw new BadRequestException("Unable to send verification email. Please try again.");
            }
        }
    }

    private void verifyBranchSuspendCode(Long branchId, String email, String verificationCode) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email confirmation is required to deactivate a branch.");
        }
        if (verificationCode == null || verificationCode.isBlank()) {
            throw new BadRequestException("Verification code is required to deactivate a branch.");
        }

        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        if (!normalizeEmail(email).equalsIgnoreCase(currentUser.getEmail())) {
            throw new BadRequestException("Email does not match your account.");
        }

        BranchSuspendTokenModel token = branchSuspendTokenRepository
                .findTopByBranchIdAndUserIdAndUsedFalseOrderByCreatedAtDesc(branchId, currentUser.getId())
                .orElseThrow(() -> new BadRequestException("No verification code found. Please send a new code."));

        if (token.isExpired()) {
            throw new BadRequestException("Verification code has expired. Please send a new code.");
        }
        if (!token.getVerificationCode().equals(verificationCode.trim())) {
            throw new BadRequestException("Invalid verification code.");
        }

        token.setUsed(true);
        branchSuspendTokenRepository.save(token);
    }

    @Override
    @Transactional
    public UserResponse createBranchManager(Long branchId, CreateBranchManagerRequest request) {
        assertAdminOrDirector();

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

    @Override
    @Transactional
    public UserResponse assignStaffToBranch(Long branchId, AssignStaffRequest request) {
        assertAdminOrDirector();

        BranchModel branch = findBranchOrThrow(branchId);
        UserModel user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found."));

        UserRole targetRole = parseStaffRole(request.getRole());
        UserRole userRole = user.getRole();
        if (userRole == null || userRole.toWebRole() != targetRole.toWebRole()) {
            throw new BadRequestException("User role does not match the requested assignment.");
        }

        if (targetRole == UserRole.BRANCH_MANAGER) {
            boolean replaceExisting = Boolean.TRUE.equals(request.getReplaceExisting());
            if (branch.getManagerId() != null && !branch.getManagerId().equals(user.getId())) {
                if (!replaceExisting) {
                    throw new ConflictException("Branch already has a manager. Confirm replacement to continue.");
                }
                clearBranchManagerLink(branchId, branch.getManagerId());
            }
            if (user.getBranchId() != null && !user.getBranchId().equals(branchId)) {
                clearBranchManagerLink(user.getBranchId(), user.getId());
            }
            user.setBranchId(branchId);
            branch.setManagerId(user.getId());
            branchRepository.save(branch);
        } else {
            if (user.getBranchId() != null && !user.getBranchId().equals(branchId)) {
                if (userRole == UserRole.BRANCH_MANAGER) {
                    clearBranchManagerLink(user.getBranchId(), user.getId());
                }
            }
            user.setBranchId(branchId);
        }

        return branchMapper.toUserResponse(userRepository.save(user));
    }

    private UserRole parseStaffRole(String role) {
        if (role == null || role.isBlank()) {
            throw new BadRequestException("Role is required.");
        }
        try {
            return UserRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid role.");
        }
    }

    private void clearBranchManagerLink(Long branchId, Long managerId) {
        branchRepository.findById(branchId).ifPresent(oldBranch -> {
            if (managerId.equals(oldBranch.getManagerId())) {
                oldBranch.setManagerId(null);
                branchRepository.save(oldBranch);
            }
        });
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
