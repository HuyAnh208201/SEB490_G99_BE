package base.api.feature.auth.service.impl;

import base.api.feature.auth.dto.request.ChangePasswordDto;
import base.api.feature.auth.dto.request.CompleteForgotPasswordDto;
import base.api.feature.auth.dto.request.CreateUserByAdminDto;
import base.api.feature.auth.dto.request.RegisterDto;
import base.api.feature.auth.dto.request.UpdateProfileDto;
import base.api.feature.auth.dto.response.InitiateForgotPasswordResponse;
import base.api.feature.auth.repository.CriticalUserActionTokenRepository;
import base.api.feature.auth.repository.IRoleRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.posorder.repository.OrderRepository;
import base.api.feature.auth.dto.response.CriticalRoleSlotsResponse;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.CriticalUserActionTokenModel;
import base.api.shared.entity.EmailVerificationTokenModel;
import base.api.shared.entity.PasswordResetTokenModel;
import base.api.shared.entity.RoleModel;
import base.api.shared.entity.UserModel;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.enums.UserGender;
import base.api.shared.enums.UserRole;
import base.api.feature.auth.repository.IEmailVerificationTokenRepository;
import base.api.feature.auth.repository.IPasswordResetTokenRepository;
import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.auth.service.IUserService;
import base.api.feature.shift.repository.ShiftAssignmentRepository;
import base.api.feature.shiftsession.repository.ShiftSessionRepository;
import base.api.shared.config.EmailService;
import base.api.shared.config.FrontendOrigin;
import base.api.shared.config.VerificationCodeIssuer;
import base.api.shared.entity.ShiftAssignmentModel;
import base.api.shared.enums.ShiftSessionStatus;
import base.api.shared.enums.ShiftStatus;
import base.api.shared.security.CurrentUserProvider;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ConflictException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Slf4j
public class UserService implements IUserService {

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IRoleRepository roleRepository;

    @Autowired
    private IBranchRepository branchRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private IPasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private IEmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private VerificationCodeIssuer verificationCodeIssuer;

    @Autowired
    private CriticalUserActionTokenRepository criticalUserActionTokenRepository;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private ShiftSessionRepository shiftSessionRepository;

    @Autowired
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Value("${url.api-url:http://localhost:1328}")
    private String apiBaseUrl;

    @Value("${url.client-url:http://localhost:5175}")
    private String clientBaseUrl;

    @Override
    public UserModel createUser(UserModel model) {
        return userRepository.save(model);
    }

    @Override
    public UserModel findByUserName(String userName) {
        return userRepository.findByUserName(userName).orElse(null);
    }


    @Override
    public boolean existedByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public UserModel findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public UserModel getOrCreateGuestByPhone(String phone, String fullName) {
        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("Số điện thoại không được để trống");
        }
        String normalized = phone.trim().replaceAll("\\s+", "");
        String name = fullName == null || fullName.isBlank()
                ? "Khách vãng lai"
                : fullName.trim();
        return userRepository.findByPhone(normalized)
                .orElseGet(() -> {
                    UserModel guest = new UserModel();
                    guest.setUserName("walkin_" + normalized);
                    guest.setPhone(normalized);
                    guest.setEmail("walkin_" + normalized + "@guest.chainstore.com");
                    guest.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
                    guest.setFullName(name);
                    guest.setRole(UserRole.CUSTOMER);
                    guest.setVerified(true);
                    guest.setActive(true);
                    return userRepository.save(guest);
                });
    }

    @Override
    @Transactional
    public UserModel registerUser(RegisterDto dto) {
        if (dto.getPhone() == null || dto.getPhone().trim().isEmpty()) {
            throw new IllegalArgumentException("Số điện thoại không được để trống");
        }

        String normalizedUserName = normalizeLogin(dto.getUserName());
        String normalizedEmail = normalizeEmail(dto.getEmail());
        String normalizedPhone = dto.getPhone().trim().replaceAll("\\s+", "");

        if (userRepository.existsByUserName(normalizedUserName)) {
            throw new IllegalArgumentException("Username đã tồn tại");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email đã được sử dụng");
        }

        UserModel newUser = new UserModel();
        newUser.setUserName(normalizedUserName);
        newUser.setEmail(normalizedEmail);
        newUser.setFirstName(dto.getFirstName());
        newUser.setPhone(normalizedPhone);
        newUser.setLastName(dto.getLastName());
        newUser.setRole(dto.getRole() != null ? dto.getRole() : UserRole.CUSTOMER);
        newUser.setGender(UserGender.MALE);
        newUser.setPassword(passwordEncoder.encode(dto.getPassword()));
        newUser.setVerified(false);

        UserModel savedUser = userRepository.save(newUser);

        String verificationToken = java.util.UUID.randomUUID().toString();
        String verificationCode = verificationCodeIssuer.issueCode();

        EmailVerificationTokenModel tokenModel = new EmailVerificationTokenModel();
        tokenModel.setVerificationToken(verificationToken);
        tokenModel.setVerificationCode(verificationCode);
        tokenModel.setEmail(savedUser.getEmail());
        tokenModel.setUserId(savedUser.getId());
        tokenModel.setExpiresAt(java.time.LocalDateTime.now().plusHours(48));
        emailVerificationTokenRepository.save(tokenModel);
        try {
            String subject = "Chúc mừng đăng ký và xác thực tài khoản";
            String fullName = (dto.getFirstName() != null ? dto.getFirstName() : "") +
                    (dto.getLastName() != null ? " " + dto.getLastName() : "");
            if (fullName.trim().isEmpty()) {
                fullName = dto.getUserName();
            }

            String verifyUrl = apiBaseUrl + "/api/auth/verify-email?token=" + verificationToken;

            String body = String.format(
                    "<html>" +
                            "<body style='font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>" +
                            "<div style='max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 10px;'>" +
                            "<div style='text-align: center; margin-bottom: 30px;'>" +
                            "<h1 style='color: #0f172a; margin: 0;'>Chúc mừng bạn đã đăng ký!</h1>" +
                            "</div>" +
                            "<h2 style='color: #0f172a;'>Xin chào %s!</h2>" +
                            "<p>Cảm ơn bạn đã đăng ký tài khoản. Để hoàn tất và có thể đăng nhập, vui lòng xác thực email bằng cách click nút bên dưới.</p>" +
                            "<div style='text-align: center; margin: 30px 0;'>" +
                            "<a href='%s' style='background-color: #8cf425; color: #0f172a; padding: 12px 30px; text-decoration: none; border-radius: 6px; display: inline-block; font-weight: 600;'>Xác nhận tài khoản</a>" +
                            "</div>" +
                            "<p style='color: #666; font-size: 14px;'>Link xác thực sẽ hết hạn sau 48 giờ.</p>" +
                            "<div style='background-color: #f9f9f9; padding: 15px; border-radius: 5px; margin: 20px 0;'>" +
                            "<h3 style='color: #0f172a; margin-top: 0;'>Thông tin tài khoản:</h3>" +
                            "<p><strong>Tên đăng nhập:</strong> %s</p>" +
                            "<p><strong>Email:</strong> %s</p>" +
                            "</div>" +
                            "<div style='background-color: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0;'>" +
                            "<p style='margin: 0; color: #856404;'><strong>Lưu ý:</strong> Bạn cần xác thực email để có thể đăng nhập vào hệ thống.</p>" +
                            "</div>" +
                            "<hr style='border: none; border-top: 1px solid #e0e0e0; margin: 30px 0;'>" +
                            "<p style='color: #999; font-size: 12px; text-align: center;'>© 2024 ChainStore. All rights reserved.</p>" +
                            "</div>" +
                            "</body>" +
                            "</html>",
                    fullName,
                    verifyUrl,
                    savedUser.getUserName(),
                    savedUser.getEmail()
            );

            emailService.sendHtmlEmail(savedUser.getEmail(), subject, body);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", savedUser.getEmail(), e.getMessage(), e);
            if (verificationCodeIssuer.isMock()) {
                log.warn("SMTP failed in mock OTP mode; demo code remains valid for {}", savedUser.getEmail());
            } else {
                throw new RuntimeException("Không thể gửi email xác thực. Vui lòng thử lại sau.", e);
            }
        }

        return savedUser;
    }

    @Override
    public List<UserModel> getAllUsers() {
        UserModel actor;
        UserRole actorRole;
        try {
            actor = currentUserProvider.getCurrentUserOrThrow();
            actorRole = actor.getRole() == null ? null : actor.getRole().toWebRole();
        } catch (Exception ex) {
            return userRepository.findAll().stream()
                    .filter(u -> u.getRole() != UserRole.CUSTOMER)
                    .toList();
        }

        List<UserModel> all = userRepository.findAll().stream()
                .filter(u -> u.getRole() != UserRole.CUSTOMER)
                .toList();
        if (actorRole != UserRole.BRANCH_MANAGER) {
            return all;
        }

        Long branchId = actor.getBranchId();
        return all.stream()
                .filter(u -> isVisibleToBranchManager(u, branchId))
                .toList();
    }

    @Override
    public Page<UserModel> getUserPage(
            PageRequestDTO pageRequest,
            UserRole role,
            Long branchId,
            String status
    ) {
        PageRequestDTO query = pageRequest == null ? new PageRequestDTO() : pageRequest;
        UserModel actor = currentUserProvider.getCurrentUserOrThrow();
        UserRole actorRole = actor.getRole() == null ? null : actor.getRole().toWebRole();
        if (actorRole != UserRole.ADMIN && actorRole != UserRole.DIRECTOR && actorRole != UserRole.BRANCH_MANAGER) {
            throw new ForbiddenException("You do not have permission to list users.");
        }

        Specification<UserModel> specification = (root, ignored, cb) ->
                cb.notEqual(root.get("roleEntity").get("name"), UserRole.CUSTOMER.name());
        if (actorRole == UserRole.BRANCH_MANAGER) {
            specification = specification.and((root, ignored, cb) -> cb.or(
                    root.get("roleEntity").get("name").in(UserRole.ADMIN.name(), UserRole.DIRECTOR.name()),
                    cb.equal(root.get("branchId"), actor.getBranchId())
            ));
        }

        String search = query.normalizedSearch();
        if (search != null) {
            String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, ignored, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(cb.lower(root.get("phone")), pattern)
            ));
        }
        if (role != null) {
            specification = specification.and((root, ignored, cb) ->
                    cb.equal(root.get("roleEntity").get("name"), role.name()));
        }
        if (branchId != null) {
            specification = specification.and((root, ignored, cb) -> cb.equal(root.get("branchId"), branchId));
        }
        if (status != null && !status.isBlank()) {
            specification = specification.and((root, ignored, cb) ->
                    cb.equal(cb.lower(root.get("status")), status.trim().toLowerCase(Locale.ROOT)));
        }

        return userRepository.findAll(
                specification,
                query.toPageable(
                        "createdAt",
                        Sort.Direction.DESC,
                        Set.of("id", "email", "fullName", "status", "createdAt")));
    }

    private boolean isVisibleToBranchManager(UserModel user, Long branchId) {
        UserRole role = user.getRole() == null ? null : user.getRole().toWebRole();
        if (role == UserRole.ADMIN || role == UserRole.DIRECTOR) {
            return true;
        }
        return branchId != null && branchId.equals(user.getBranchId());
    }

    @Override
    @Transactional
    public InitiateForgotPasswordResponse initiateForgotPassword(String contactInfo) throws Exception {
        return initiateForgotPassword(contactInfo, null);
    }

    @Override
    @Transactional
    public InitiateForgotPasswordResponse initiateForgotPassword(String contactInfo, String frontendBaseUrl)
            throws Exception {
        if (contactInfo == null || contactInfo.trim().isEmpty()) {
            throw new IllegalArgumentException("Thông tin liên hệ không được để trống");
        }
        String normalized = contactInfo.trim().toLowerCase();
        UserModel user;
        if (normalized.contains("@")) {
            user = userRepository.findByEmail(normalized).orElse(null);
        } else {
            user = userRepository.findByUserName(normalized).orElse(null);
        }

        if (user == null) {
            throw new Exception("Không tìm thấy tài khoản với thông tin này");
        }

        passwordResetTokenRepository.deleteByUserId(user.getId());

        String resetToken = java.util.UUID.randomUUID().toString();

        PasswordResetTokenModel tokenModel = new PasswordResetTokenModel();
        tokenModel.setResetToken(resetToken);
        tokenModel.setEmail(user.getEmail());
        tokenModel.setUserId(user.getId());
        tokenModel.setExpiresAt(java.time.LocalDateTime.now().plusHours(1));
        passwordResetTokenRepository.save(tokenModel);
        try {
            String subject = "Đặt lại mật khẩu";
            String fullName = (user.getFirstName() != null ? user.getFirstName() : "") +
                    (user.getLastName() != null ? " " + user.getLastName() : "");
            if (fullName.trim().isEmpty()) {
                fullName = user.getUserName();
            }

            String resetUrl = FrontendOrigin.path(
                    staffFrontendOrigin(frontendBaseUrl),
                    "/reset-password?token=" + resetToken);

            String body = String.format(
                    "<html>" +
                            "<body style='font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>" +
                            "<div style='max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 10px;'>" +
                            "<div style='text-align: center; margin-bottom: 30px;'>" +
                            "<h1 style='color: #0f172a; margin: 0;'>Đặt lại mật khẩu</h1>" +
                            "</div>" +
                            "<h2 style='color: #0f172a;'>Xin chào %s!</h2>" +
                            "<p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.</p>" +
                            "<p>Bạn có thể click vào nút bên dưới để đặt lại mật khẩu. Link này sẽ hết hạn sau 1 giờ.</p>" +
                            "<div style='text-align: center; margin: 30px 0;'>" +
                            "<a href='%s' style='background-color: #8cf425; color: #0f172a; padding: 12px 30px; text-decoration: none; border-radius: 6px; display: inline-block; font-weight: 600;'>Đặt lại mật khẩu</a>" +
                            "</div>" +
                            "<div style='background-color: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0;'>" +
                            "<p style='margin: 0; color: #856404;'><strong>Lưu ý:</strong> Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.</p>" +
                            "</div>" +
                            "<hr style='border: none; border-top: 1px solid #e0e0e0; margin: 30px 0;'>" +
                            "<p style='color: #999; font-size: 12px; text-align: center;'>© 2024 ChainStore. All rights reserved.</p>" +
                            "</div>" +
                            "</body>" +
                            "</html>",
                    fullName,
                    resetUrl
            );

            emailService.sendHtmlEmail(user.getEmail(), subject, body);
        } catch (Exception e) {
            log.error("Failed to send reset password email to {}: {}", user.getEmail(), e.getMessage(), e);
            throw new Exception("Không thể gửi email. Vui lòng thử lại sau.");
        }

        InitiateForgotPasswordResponse response = new InitiateForgotPasswordResponse();
        response.setMessage("Link đặt lại mật khẩu đã được gửi đến email của bạn");
        return response;
    }

    @Override
    @Transactional
    public void completeForgotPassword(CompleteForgotPasswordDto dto) throws Exception {
        if (!dto.getNewPassword().equals(dto.getConfirmNewPassword())) {
            throw new Exception("Mật khẩu xác nhận không khớp");
        }

        if (dto.getNewPassword().length() < 6) {
            throw new Exception("Mật khẩu phải có ít nhất 6 ký tự");
        }

        PasswordResetTokenModel tokenModel = passwordResetTokenRepository
                .findByResetToken(dto.getResetToken())
                .orElseThrow(() -> new Exception("Token không hợp lệ"));

        if (tokenModel.isUsed()) {
            throw new Exception("Token đã được sử dụng");
        }

        if (tokenModel.isExpired()) {
            throw new Exception("Token đã hết hạn");
        }

        UserModel user = userRepository.findById(tokenModel.getUserId())
                .orElseThrow(() -> new Exception("Không tìm thấy người dùng"));

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);

        tokenModel.setUsed(true);
        passwordResetTokenRepository.save(tokenModel);

        try {
            String subject = "Mật khẩu đã được thay đổi thành công";
            String fullName = (user.getFirstName() != null ? user.getFirstName() : "") +
                    (user.getLastName() != null ? " " + user.getLastName() : "");
            if (fullName.trim().isEmpty()) {
                fullName = user.getUserName();
            }

            String body = String.format(
                    "<html>" +
                            "<body style='font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>" +
                            "<div style='max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 10px;'>" +
                            "<div style='text-align: center; margin-bottom: 30px;'>" +
                            "<h1 style='color: #4caf50; margin: 0;'>Mật khẩu đã được thay đổi</h1>" +
                            "</div>" +
                            "<h2 style='color: #0f172a;'>Xin chào %s!</h2>" +
                            "<p>Mật khẩu tài khoản của bạn đã được thay đổi thành công.</p>" +
                            "<p>Nếu bạn không thực hiện thay đổi này, vui lòng liên hệ hỗ trợ ngay lập tức.</p>" +
                            "<div style='background-color: #f9f9f9; padding: 15px; border-radius: 5px; margin: 20px 0;'>" +
                            "<p><strong>Tên đăng nhập:</strong> %s</p>" +
                            "<p><strong>Email:</strong> %s</p>" +
                            "</div>" +
                            "<hr style='border: none; border-top: 1px solid #e0e0e0; margin: 30px 0;'>" +
                            "<p style='color: #999; font-size: 12px; text-align: center;'>© 2024 ChainStore. All rights reserved.</p>" +
                            "</div>" +
                            "</body>" +
                            "</html>",
                    fullName,
                    user.getUserName(),
                    user.getEmail()
            );

            emailService.sendHtmlEmail(user.getEmail(), subject, body);
        } catch (Exception e) {
            log.warn("Failed to send password reset confirmation email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    @Override
    @Transactional
    public void verifyEmailByToken(String token) throws Exception {
        EmailVerificationTokenModel tokenModel = emailVerificationTokenRepository
                .findByVerificationToken(token)
                .orElseThrow(() -> new Exception("Token không hợp lệ"));

        if (tokenModel.isUsed()) {
            throw new Exception("Token đã được sử dụng");
        }

        if (tokenModel.isExpired()) {
            throw new Exception("Token đã hết hạn");
        }

        UserModel user = userRepository.findById(tokenModel.getUserId())
                .orElseThrow(() -> new Exception("Không tìm thấy người dùng"));

        user.setVerified(true);
        userRepository.save(user);

        tokenModel.setUsed(true);
        emailVerificationTokenRepository.save(tokenModel);
        try {
            String subject = "Chào mừng bạn!";
            String fullName = (user.getFirstName() != null ? user.getFirstName() : "") +
                    (user.getLastName() != null ? " " + user.getLastName() : "");
            if (fullName.trim().isEmpty()) {
                fullName = user.getUserName();
            }

            String body = String.format(
                    "<html>" +
                            "<body style='font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>" +
                            "<div style='max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 10px;'>" +
                            "<div style='text-align: center; margin-bottom: 30px;'>" +
                            "<h1 style='color: #0f172a; margin: 0;'>Chào mừng bạn!</h1>" +
                            "</div>" +
                            "<h2 style='color: #0f172a;'>Xin chào %s!</h2>" +
                            "<p>Email của bạn đã được xác thực thành công! 🎉</p>" +
                            "<p>Cảm ơn bạn đã đăng ký tài khoản!</p>" +
                            "<div style='background-color: #f9f9f9; padding: 15px; border-radius: 5px; margin: 20px 0;'>" +
                            "<h3 style='color: #0f172a; margin-top: 0;'>Thông tin tài khoản:</h3>" +
                            "<p><strong>Tên đăng nhập:</strong> %s</p>" +
                            "<p><strong>Email:</strong> %s</p>" +
                            "<p><strong>Trạng thái:</strong> <span style='color: #4caf50; font-weight: bold;'>✓ Đã xác thực</span></p>" +
                            "</div>" +
                            "<p>Bạn có thể bắt đầu sử dụng hệ thống ngay bây giờ!</p>" +
                            "<div style='text-align: center; margin: 30px 0;'>" +
                            "<a href='%s' style='background-color: #8cf425; color: #0f172a; padding: 12px 30px; text-decoration: none; border-radius: 6px; display: inline-block; font-weight: 600;'>Truy cập hệ thống</a>" +
                            "</div>" +
                            "<p style='color: #666; font-size: 14px;'>Nếu bạn có bất kỳ câu hỏi nào, đừng ngần ngại liên hệ với chúng tôi.</p>" +
                            "<hr style='border: none; border-top: 1px solid #e0e0e0; margin: 30px 0;'>" +
                            "<p style='color: #999; font-size: 12px; text-align: center;'>© 2024 ChainStore. All rights reserved.</p>" +
                            "</div>" +
                            "</body>" +
                            "</html>",
                    fullName,
                    user.getUserName(),
                    user.getEmail(),
                    staffFrontendOrigin(null)
            );

            emailService.sendHtmlEmail(user.getEmail(), subject, body);
        } catch (Exception e) {
            log.warn("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    @Override
    @Transactional
    public void resendVerificationEmail(String contactInfo) throws Exception {
        if (contactInfo == null || contactInfo.trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập email hoặc tên đăng nhập");
        }
        String normalized = contactInfo.trim();
        UserModel user;
        if (normalized.contains("@")) {
            user = userRepository.findByEmail(normalizeEmail(normalized)).orElse(null);
        } else {
            user = userRepository.findByUserName(normalizeLogin(normalized)).orElse(null);
        }
        if (user == null) {
            throw new Exception("Không tìm thấy tài khoản với thông tin này");
        }
        if (user.isVerified()) {
            throw new IllegalArgumentException("Tài khoản đã được xác thực, không cần gửi lại email");
        }

        emailVerificationTokenRepository.deleteByEmail(user.getEmail());

        String verificationToken = java.util.UUID.randomUUID().toString();
        String verificationCode = verificationCodeIssuer.issueCode();

        EmailVerificationTokenModel tokenModel = new EmailVerificationTokenModel();
        tokenModel.setVerificationToken(verificationToken);
        tokenModel.setVerificationCode(verificationCode);
        tokenModel.setEmail(user.getEmail());
        tokenModel.setUserId(user.getId());
        tokenModel.setExpiresAt(java.time.LocalDateTime.now().plusHours(48));
        emailVerificationTokenRepository.save(tokenModel);

        String fullName = (user.getFirstName() != null ? user.getFirstName() : "") +
                (user.getLastName() != null ? " " + user.getLastName() : "");
        if (fullName.trim().isEmpty()) {
            fullName = user.getUserName();
        }

        String verifyUrl = apiBaseUrl + "/api/auth/verify-email?token=" + verificationToken;

        String body = String.format(
                "<html>" +
                        "<body style='font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>" +
                        "<div style='max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 10px;'>" +
                        "<div style='text-align: center; margin-bottom: 30px;'>" +
                        "<h1 style='color: #0f172a; margin: 0;'>Xác thực email tài khoản</h1>" +
                        "</div>" +
                        "<h2 style='color: #0f172a;'>Xin chào %s!</h2>" +
                        "<p>Bạn vừa yêu cầu gửi lại email xác thực. Vui lòng click nút bên dưới để xác thực tài khoản.</p>" +
                        "<div style='text-align: center; margin: 30px 0;'>" +
                        "<a href='%s' style='background-color: #8cf425; color: #0f172a; padding: 12px 30px; text-decoration: none; border-radius: 6px; display: inline-block; font-weight: 600;'>Xác nhận tài khoản</a>" +
                        "</div>" +
                        "<p style='color: #666; font-size: 14px;'>Link xác thực sẽ hết hạn sau 48 giờ.</p>" +
                        "<div style='background-color: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0;'>" +
                        "<p style='margin: 0; color: #856404;'><strong>Lưu ý:</strong> Nếu bạn không yêu cầu gửi lại email này, vui lòng bỏ qua.</p>" +
                        "</div>" +
                        "<hr style='border: none; border-top: 1px solid #e0e0e0; margin: 30px 0;'>" +
                        "<p style='color: #999; font-size: 12px; text-align: center;'>© 2024 ChainStore. All rights reserved.</p>" +
                        "</div>" +
                        "</body>" +
                        "</html>",
                fullName,
                verifyUrl
        );

        try {
            emailService.sendHtmlEmail(user.getEmail(), "Gửi lại email xác thực tài khoản", body);
        } catch (Exception e) {
            log.error("Failed to resend verification email to {}: {}", user.getEmail(), e.getMessage(), e);
            if (verificationCodeIssuer.isMock()) {
                log.warn("SMTP failed in mock OTP mode; demo code remains valid for {}", user.getEmail());
            } else {
                throw new RuntimeException("Không thể gửi email xác thực. Vui lòng thử lại sau.", e);
            }
        }
    }

    @Override
    @Transactional
    public UserModel updateProfile(Long userId, UpdateProfileDto dto) {
        UserModel user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        String normalizedEmail = normalizeEmail(dto.getEmail());
        if (normalizedEmail == null || normalizedEmail.isEmpty()) {
            throw new IllegalArgumentException("Email không được để trống");
        }
        if (!normalizedEmail.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(normalizedEmail)) {
            throw new RuntimeException("Email đã được sử dụng bởi tài khoản khác");
        }

        if (dto.getPhone() != null && !dto.getPhone().trim().isEmpty()) {
            String normalizedPhone = dto.getPhone().trim().replaceAll("\\s+", "");
            user.setPhone(normalizedPhone);
        }

        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(normalizedEmail);
        user.setAvatar(dto.getAvatar());
        user.setBirthDate(dto.getBirthDate() == null ? null : dto.getBirthDate().atStartOfDay());

        if (dto.getGender() != null) {
            user.setGender(UserGender.valueOf(dto.getGender()));
        }

        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordDto dto) throws Exception {
        UserModel user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception("Không tìm thấy người dùng"));

        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new Exception("Mật khẩu cũ không đúng");
        }

        if (!dto.getNewPassword().equals(dto.getConfirmNewPassword())) {
            throw new Exception("Mật khẩu xác nhận không khớp");
        }

        if (dto.getNewPassword().length() < 6) {
            throw new Exception("Mật khẩu phải có ít nhất 6 ký tự");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public UserModel createUserByAdmin(CreateUserByAdminDto dto, UserModel creator) throws Exception {
        String normalizedUserName = normalizeLogin(dto.getUserName());
        String normalizedEmail = normalizeEmail(dto.getEmail());
        String normalizedPhone = dto.getPhone() == null ? null : dto.getPhone().trim().replaceAll("\\s+", "");

        if (creator == null) {
            throw new BadRequestException("Không xác định được người tạo tài khoản");
        }

        if (normalizedUserName != null
                && !normalizedUserName.equals(normalizedEmail)
                && userRepository.existsByUserName(normalizedUserName)) {
            throw new IllegalArgumentException("Username đã tồn tại");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email đã được sử dụng");
        }
        if (normalizedPhone != null && userRepository.existsByPhone(normalizedPhone)) {
            throw new ConflictException("Số điện thoại đã được sử dụng");
        }

        UserRole targetRole = dto.getRole();
        UserRole persistedRole = targetRole.toWebRole();
        RoleModel roleEntity = roleRepository.findByName(persistedRole.name())
                .orElseThrow(() -> new BadRequestException("Role không tồn tại"));

        Long targetBranchId = resolveTargetBranchId(targetRole, dto.getBranchId(), creator);
        BranchModel targetBranch = null;
        if (targetBranchId != null) {
            targetBranch = branchRepository.findById(targetBranchId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy chi nhánh"));
        }
        if (persistedRole == UserRole.BRANCH_MANAGER && targetBranch != null && targetBranch.getManagerId() != null) {
            throw new ConflictException("Chi nhánh đã có quản lý");
        }
        validateCriticalRoleSlot(persistedRole, null);

        String tempPassword = generateTempPassword(12);

        UserModel newUser = new UserModel();
        newUser.setUserName(normalizedUserName);
        newUser.setEmail(normalizedEmail);
        newUser.setFirstName(dto.getFirstName());
        newUser.setLastName(dto.getLastName());
        newUser.setPhone(normalizedPhone);
        newUser.setRoleEntity(roleEntity);
        newUser.setBranchId(targetBranchId);
        newUser.setGender(UserGender.MALE);
        newUser.setPassword(passwordEncoder.encode(tempPassword));
        newUser.setVerified(true);
        newUser.setActive(true);

        UserModel savedUser = userRepository.save(newUser);
        if (persistedRole == UserRole.BRANCH_MANAGER && targetBranch != null) {
            targetBranch.setManagerId(savedUser.getId());
            branchRepository.save(targetBranch);
        }

        try {
            String fullName = (dto.getFirstName() != null ? dto.getFirstName() : "") +
                    (dto.getLastName() != null ? " " + dto.getLastName() : "");
            if (fullName.trim().isEmpty()) {
                fullName = dto.getUserName();
            }

            String subject = "Tài khoản ChainStore của bạn đã được tạo";
            String body = String.format(
                    "<html>" +
                            "<body style='font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>" +
                            "<div style='max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 10px;'>" +
                            "<div style='text-align: center; margin-bottom: 30px;'>" +
                            "<h1 style='color: #0f172a; margin: 0;'>Chào mừng đến với ChainStore</h1>" +
                            "</div>" +
                            "<h2 style='color: #0f172a;'>Xin chào %s!</h2>" +
                            "<p>Quản trị viên đã tạo tài khoản cho bạn với vai trò <strong>%s</strong>. " +
                            "Dưới đây là thông tin đăng nhập tạm thời:</p>" +
                            "<div style='background-color: #f9f9f9; padding: 15px; border-radius: 5px; margin: 20px 0;'>" +
                            "<p><strong>Tên đăng nhập:</strong> %s</p>" +
                            "<p><strong>Email:</strong> %s</p>" +
                            "<p><strong>Mật khẩu tạm:</strong> <span style='font-family: monospace; background:#fff3cd; padding:4px 8px; border-radius:4px;'>%s</span></p>" +
                            "<p><strong>Vai trò:</strong> %s</p>" +
                            "</div>" +
                            "<div style='background-color: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0;'>" +
                            "<p style='margin: 0; color: #856404;'><strong>Quan trọng:</strong> Vì lý do bảo mật, vui lòng đăng nhập và đổi mật khẩu ngay sau lần đăng nhập đầu tiên.</p>" +
                            "</div>" +
                            "<div style='text-align: center; margin: 30px 0;'>" +
                            "<a href='%s' style='background-color: #8cf425; color: #0f172a; padding: 12px 30px; text-decoration: none; border-radius: 6px; display: inline-block; font-weight: 600;'>Đăng nhập ngay</a>" +
                            "</div>" +
                            "<hr style='border: none; border-top: 1px solid #e0e0e0; margin: 30px 0;'>" +
                            "<p style='color: #999; font-size: 12px; text-align: center;'>© 2024 ChainStore. All rights reserved.</p>" +
                            "</div>" +
                            "</body>" +
                            "</html>",
                    fullName,
                    dto.getRole().name(),
                    dto.getUserName(),
                    dto.getEmail(),
                    tempPassword,
                    dto.getRole().name(),
                    FrontendOrigin.path(staffFrontendOrigin(null), "/login")
            );

            emailService.sendHtmlEmail(savedUser.getEmail(), subject, body);
        } catch (Exception e) {
            log.error("Failed to send temp password email to {}: {}", savedUser.getEmail(), e.getMessage(), e);
            throw new Exception("Không thể gửi email mật khẩu tạm. Vui lòng thử lại.");
        }

        return savedUser;
    }

    // TODO: [Giang] deactivateUser — cần bổ sung findManagedTargetUser() và clearManagedBranchIfNeeded() để compile
    // @Override
    // @Transactional
    // public UserModel deactivateUser(Long targetUserId, UserModel actor) {
    //     UserModel target = findManagedTargetUser(targetUserId, actor);
    //     target.setActive(false);
    //     clearManagedBranchIfNeeded(target);
    //     return userRepository.save(target);
    // }

    private Long resolveTargetBranchId(UserRole targetRole, Long requestedBranchId, UserModel creator) {
        if (targetRole == null) {
            throw new BadRequestException("Role không được để trống");
        }

        if (!targetRole.requiresBranch()) {
            if (requestedBranchId != null) {
                throw new BadRequestException("Role này không cần gán chi nhánh");
            }
            return null;
        }

        if (requestedBranchId == null) {
            throw new BadRequestException("Vui lòng chọn chi nhánh");
        }

        UserRole creatorRole = creator.getRole();
        if (creatorRole == null) {
            throw new ForbiddenException("Không xác định được quyền của người tạo");
        }

        if (creatorRole.toWebRole() != UserRole.BRANCH_MANAGER) {
            return requestedBranchId;
        }

        Long creatorBranchId = creator.getBranchId();
        if (creatorBranchId == null) {
            throw new BadRequestException("Branch manager chưa được gán chi nhánh");
        }
        if (!creatorBranchId.equals(requestedBranchId)) {
            throw new ForbiddenException("Chỉ được tạo nhân viên cho chi nhánh của mình");
        }
        return creatorBranchId;
    }

    private String normalizeLogin(String value) {
        if (value == null) return null;
        return value.trim().toLowerCase();
    }

    private String normalizeEmail(String value) {
        if (value == null) return null;
        return value.trim().toLowerCase();
    }

    private String generateTempPassword(int length) {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnopqrstuvwxyz";
        String digits = "23456789";
        String special = "!@#$%&*";
        String all = upper + lower + digits + special;

        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        sb.append(upper.charAt(random.nextInt(upper.length())));
        sb.append(lower.charAt(random.nextInt(lower.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        sb.append(special.charAt(random.nextInt(special.length())));
        for (int i = 4; i < length; i++) {
            sb.append(all.charAt(random.nextInt(all.length())));
        }

        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    @Override
    @Transactional
    public UserModel updateUserStatus(Long targetUserId, boolean active, UserModel actor) {
        return updateUserStatus(targetUserId, active, actor, null, null);
    }

    @Override
    @Transactional
    public UserModel updateUserStatus(Long targetUserId, boolean active, UserModel actor, String email, String verificationCode) {
        assertCanManageTargetUser(actor, targetUserId);
        UserModel target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));

        if (!active && isCriticalRole(target.getRole())) {
            verifyCriticalUserAction(targetUserId, actor, "DEACTIVATE", email, verificationCode);
        }

        target.setActive(active);
        UserModel saved = userRepository.save(target);
        if (!active) {
            hardenAfterDeactivate(saved);
        }
        return saved;
    }

    /**
     * Force-close open POS sessions and remove staff from current/future published shifts.
     * Existing JWTs are rejected on the next request via {@code UserDetails.isEnabled()}.
     */
    private void hardenAfterDeactivate(UserModel target) {
        List<ShiftSessionStatus> activeStatuses = List.of(
                ShiftSessionStatus.OPEN,
                ShiftSessionStatus.CLOSING,
                ShiftSessionStatus.PENDING_HANDOVER,
                ShiftSessionStatus.SCHEDULED);
        shiftSessionRepository
                .findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(target.getId(), activeStatuses)
                .ifPresent(session -> {
                    session.setStatus(ShiftSessionStatus.CLOSED);
                    session.setClosedAt(LocalDateTime.now());
                    if (session.getClosingNote() == null || session.getClosingNote().isBlank()) {
                        session.setClosingNote("Force-closed: account deactivated.");
                    }
                    shiftSessionRepository.save(session);
                    log.info("Force-closed shift session {} for deactivated user {}", session.getId(), target.getId());
                });

        List<ShiftAssignmentModel> future = shiftAssignmentRepository.findPublishedAssignmentsFrom(
                target.getId(), LocalDateTime.now().minusMinutes(1), ShiftStatus.PUBLISHED);
        if (!future.isEmpty()) {
            shiftAssignmentRepository.deleteAll(future);
            log.info("Removed {} published assignment(s) for deactivated user {}", future.size(), target.getId());
        }
    }

    @Override
    @Transactional
    public void deleteUser(Long targetUserId, UserModel actor) {
        deleteUser(targetUserId, actor, null, null);
    }

    @Override
    @Transactional
    public void deleteUser(Long targetUserId, UserModel actor, String email, String verificationCode) {
        assertCanManageTargetUser(actor, targetUserId);
        if (actor.getId().equals(targetUserId)) {
            throw new BadRequestException("Không thể xóa tài khoản của chính mình.");
        }
        UserModel target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));

        UserRole targetRole = target.getRole();
        if (isCriticalRole(targetRole)) {
            verifyCriticalUserAction(targetUserId, actor, "DELETE", email, verificationCode);
        } else if (targetRole == UserRole.ADMIN) {
            throw new ForbiddenException("Không thể xóa tài khoản Admin.");
        }

        List<ShiftSessionStatus> activeStatuses = List.of(
                ShiftSessionStatus.OPEN,
                ShiftSessionStatus.CLOSING,
                ShiftSessionStatus.PENDING_HANDOVER);
        boolean hasOpenSession = shiftSessionRepository
                .findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(target.getId(), activeStatuses)
                .isPresent();
        if (hasOpenSession) {
            throw new BadRequestException(
                    "Cannot delete this account while a shift session is still open. Deactivate the account first.");
        }

        // Prefer deactivate for cashiers/IS with published assignments so history FKs stay intact.
        List<ShiftAssignmentModel> future = shiftAssignmentRepository.findPublishedAssignmentsFrom(
                target.getId(), LocalDateTime.now().minusMinutes(1), ShiftStatus.PUBLISHED);
        if (!future.isEmpty()) {
            throw new BadRequestException(
                    "Cannot delete this account while they are assigned to published shifts. Deactivate the account first (assignments will be cleared).");
        }

        if (orderRepository.existsByCashierId(targetUserId)) {
            throw new BadRequestException(
                    "Không thể xóa tài khoản này vì đã có lịch sử bán hàng. Vui lòng vô hiệu hóa tài khoản để giữ nguyên dữ liệu hóa đơn.");
        }

        if (targetRole == UserRole.BRANCH_MANAGER && target.getBranchId() != null) {
            branchRepository.findById(target.getBranchId()).ifPresent(branch -> {
                if (targetUserId.equals(branch.getManagerId())) {
                    branch.setManagerId(null);
                    branchRepository.save(branch);
                }
            });
        }

        userRepository.delete(target);
    }

    @Override
    @Transactional
    public void sendCriticalUserActionCode(Long targetUserId, String email, String actionType, UserModel actor) {
        assertCanManageTargetUser(actor, targetUserId);
        UserModel target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));
        if (!isCriticalRole(target.getRole())) {
            throw new BadRequestException("This account does not require verification.");
        }

        String normalizedEmail = normalizeEmail(email);
        if (!normalizeEmail(actor.getEmail()).equalsIgnoreCase(normalizedEmail)) {
            throw new BadRequestException("Email does not match your account.");
        }

        String code = verificationCodeIssuer.issueCode();
        CriticalUserActionTokenModel token = new CriticalUserActionTokenModel();
        token.setTargetUserId(targetUserId);
        token.setActorUserId(actor.getId());
        token.setActionType(actionType.toUpperCase());
        token.setVerificationCode(code);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        criticalUserActionTokenRepository.save(token);

        String fullName = buildDisplayName(actor);
        String subject = "ChainStore — Critical account action verification";
        String body = String.format(
                "<html><body style='font-family: Arial, sans-serif;'>"
                        + "<h2>Verify critical account action</h2>"
                        + "<p>Hello %s,</p>"
                        + "<p>You requested to <strong>%s</strong> the account <strong>%s</strong>.</p>"
                        + "<p style='font-size:28px;font-weight:bold;letter-spacing:6px;color:#0058be;'>%s</p>"
                        + "<p>This code expires in 15 minutes.</p>"
                        + "</body></html>",
                fullName,
                actionType.toLowerCase(),
                buildDisplayName(target),
                code);
        try {
            emailService.sendHtmlEmail(normalizedEmail, subject, body);
        } catch (Exception ex) {
            if (verificationCodeIssuer.isMock()) {
                log.warn("SMTP failed in mock OTP mode; demo code remains valid for {}", normalizedEmail);
            } else {
                throw new BadRequestException("Unable to send verification email. Please try again.");
            }
        }
    }

    @Override
    public CriticalRoleSlotsResponse getCriticalRoleSlots() {
        CriticalRoleSlotsResponse response = new CriticalRoleSlotsResponse();
        response.setAdminAvailable(userRepository.countActiveByRoleName(UserRole.ADMIN.name()) == 0);
        response.setDirectorAvailable(userRepository.countActiveByRoleName(UserRole.DIRECTOR.name()) == 0);
        response.setWarehouseManagerAvailable(
                userRepository.countActiveByRoleName(UserRole.WAREHOUSE_MANAGER.name()) == 0);
        return response;
    }

    private void validateCriticalRoleSlot(UserRole role, Long excludeUserId) {
        if (role == null) {
            return;
        }
        UserRole web = role.toWebRole();
        long count = excludeUserId == null
                ? userRepository.countActiveByRoleName(web.name())
                : userRepository.countActiveByRoleNameExcluding(web.name(), excludeUserId);
        if ((web == UserRole.ADMIN || web == UserRole.DIRECTOR || web == UserRole.WAREHOUSE_MANAGER) && count > 0) {
            throw new ConflictException("Only one active " + web.name() + " account is allowed in the system.");
        }
    }

    private boolean isCriticalRole(UserRole role) {
        if (role == null) {
            return false;
        }
        UserRole web = role.toWebRole();
        return web == UserRole.ADMIN || web == UserRole.DIRECTOR;
    }

    private void verifyCriticalUserAction(
            Long targetUserId,
            UserModel actor,
            String actionType,
            String email,
            String verificationCode) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email confirmation is required for this action.");
        }
        if (verificationCode == null || verificationCode.isBlank()) {
            throw new BadRequestException("Verification code is required for this action.");
        }
        if (!normalizeEmail(email).equalsIgnoreCase(normalizeEmail(actor.getEmail()))) {
            throw new BadRequestException("Email does not match your account.");
        }

        CriticalUserActionTokenModel token = criticalUserActionTokenRepository
                .findTopByTargetUserIdAndActorUserIdAndActionTypeAndUsedFalseOrderByCreatedAtDesc(
                        targetUserId, actor.getId(), actionType.toUpperCase())
                .orElseThrow(() -> new BadRequestException("No verification code found. Please send a new code."));
        if (token.isExpired()) {
            throw new BadRequestException("Verification code has expired. Please send a new code.");
        }
        if (!token.getVerificationCode().equals(verificationCode.trim())) {
            throw new BadRequestException("Invalid verification code.");
        }
        token.setUsed(true);
        criticalUserActionTokenRepository.save(token);
    }

    private String buildDisplayName(UserModel user) {
        String fullName = (user.getFirstName() != null ? user.getFirstName() : "")
                + (user.getLastName() != null ? " " + user.getLastName() : "");
        fullName = fullName.trim();
        return fullName.isEmpty() ? user.getUserName() : fullName;
    }

    private void assertCanManageTargetUser(UserModel actor, Long targetUserId) {
        if (actor == null || actor.getRole() == null) {
            throw new ForbiddenException("Không xác định được quyền của người thực hiện.");
        }
        if (!actor.getRole().canManageUsers()) {
            throw new ForbiddenException("Không có quyền quản lý user");
        }
        if (actor.getId().equals(targetUserId)) {
            throw new BadRequestException("Không thể thao tác trên tài khoản của chính mình.");
        }

        UserModel target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));
        UserRole actorRole = actor.getRole().toWebRole();
        UserRole targetRole = target.getRole() != null ? target.getRole().toWebRole() : null;

        if (actorRole == UserRole.ADMIN || actorRole == UserRole.DIRECTOR) {
            if (targetRole == UserRole.ADMIN && actorRole != UserRole.ADMIN) {
                throw new ForbiddenException("Director không thể quản lý tài khoản Admin.");
            }
            return;
        }

        if (actorRole == UserRole.BRANCH_MANAGER) {
            if (targetRole != UserRole.CASHIER && targetRole != UserRole.INVENTORY_STAFF) {
                throw new ForbiddenException("Branch manager chỉ được quản lý Cashier và Inventory staff.");
            }
            if (actor.getBranchId() == null || target.getBranchId() == null
                    || !actor.getBranchId().equals(target.getBranchId())) {
                throw new ForbiddenException("Chỉ được quản lý nhân viên tại chi nhánh của mình.");
            }
            return;
        }

        throw new ForbiddenException("Không có quyền quản lý user");
    }

    private String staffFrontendOrigin(String requested) {
        return FrontendOrigin.resolve(requested, clientBaseUrl);
    }
}
