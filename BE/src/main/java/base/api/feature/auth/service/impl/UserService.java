package base.api.feature.auth.service.impl;

import base.api.feature.auth.dto.request.ChangePasswordDto;
import base.api.feature.auth.dto.request.CompleteForgotPasswordDto;
import base.api.feature.auth.dto.request.CreateUserByAdminDto;
import base.api.feature.auth.dto.request.RegisterDto;
import base.api.feature.auth.dto.request.UpdateProfileDto;
import base.api.feature.auth.dto.response.InitiateForgotPasswordResponse;
import base.api.feature.auth.repository.IRoleRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.EmailVerificationTokenModel;
import base.api.shared.entity.PasswordResetTokenModel;
import base.api.shared.entity.RoleModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserGender;
import base.api.shared.enums.UserRole;
import base.api.feature.auth.repository.IEmailVerificationTokenRepository;
import base.api.feature.auth.repository.IPasswordResetTokenRepository;
import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.auth.service.IUserService;
import base.api.shared.config.EmailService;
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

import java.util.List;

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

    @Value("${url.api-url:http://localhost:1328}")
    private String apiBaseUrl;

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
    public UserModel getOrCreateGuestByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("Số điện thoại không được để trống");
        }
        String normalized = phone.trim().replaceAll("\\s+", "");
        return userRepository.findByPhone(normalized)
                .orElseGet(() -> {
                    UserModel guest = new UserModel();
                    guest.setUserName("walkin_" + normalized);
                    guest.setPhone(normalized);
                    guest.setEmail("walkin_" + normalized + "@guest.chainstore.com");
                    guest.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
                    guest.setFirstName("Khách");
                    guest.setLastName("Vãng lai");
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
        String verificationCode = String.format("%06d", new java.security.SecureRandom().nextInt(1_000_000));

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
            if(fullName.trim().isEmpty()) {
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
            throw new RuntimeException("Không thể gửi email xác thực. Vui lòng thử lại sau.", e);
        }

        return savedUser;
    }

    @Override
    public List<UserModel> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional
    public InitiateForgotPasswordResponse initiateForgotPassword(String contactInfo) throws Exception {
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
            if(fullName.trim().isEmpty()) {
                fullName = user.getUserName();
            }

            String resetUrl = "https://localhost:5173/reset-password?token=" + resetToken;

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
            if(fullName.trim().isEmpty()) {
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
                            "<a href='https://localhost:5173/' style='background-color: #8cf425; color: #0f172a; padding: 12px 30px; text-decoration: none; border-radius: 6px; display: inline-block; font-weight: 600;'>Truy cập hệ thống</a>" +
                            "</div>" +
                            "<p style='color: #666; font-size: 14px;'>Nếu bạn có bất kỳ câu hỏi nào, đừng ngần ngại liên hệ với chúng tôi.</p>" +
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
        String verificationCode = String.format("%06d", new java.security.SecureRandom().nextInt(1_000_000));

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
            throw new RuntimeException("Không thể gửi email xác thực. Vui lòng thử lại sau.", e);
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
        user.setBirthDate(dto.getBirthDate());

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
                    "https://localhost:5173/login"
            );

            emailService.sendHtmlEmail(savedUser.getEmail(), subject, body);
        } catch (Exception e) {
            log.error("Failed to send temp password email to {}: {}", savedUser.getEmail(), e.getMessage(), e);
            throw new Exception("Không thể gửi email mật khẩu tạm. Vui lòng thử lại.");
        }

        return savedUser;
    }

    @Override
    @Transactional
    public UserModel deactivateUser(Long targetUserId, UserModel actor) {
        UserModel target = findManagedTargetUser(targetUserId, actor);
        target.setActive(false);
        clearManagedBranchIfNeeded(target);
        return userRepository.save(target);
    }

    @Override
    @Transactional
    public void deleteUser(Long targetUserId, UserModel actor) {
        UserModel target = findManagedTargetUser(targetUserId, actor);
        clearManagedBranchIfNeeded(target);
        userRepository.delete(target);
    }

    private Long resolveTargetBranchId(UserRole targetRole, Long requestedBranchId, UserModel creator) {
        if (targetRole == null) {
            throw new BadRequestException("Role không được để trống");
        }

        UserRole creatorRole = creator.getRole();
        if (creatorRole == null) {
            throw new ForbiddenException("Không xác định được quyền của người tạo");
        }

        if (!targetRole.requiresBranch()) {
            if (requestedBranchId != null) {
                throw new BadRequestException("Role này không cần gán chi nhánh");
            }
            return null;
        }

        if (creatorRole.toWebRole() == UserRole.BRANCH_MANAGER) {
            Long creatorBranchId = creator.getBranchId();
            if (creatorBranchId == null) {
                throw new BadRequestException("Branch manager chưa được gán chi nhánh");
            }
            return creatorBranchId;
        }

        if (requestedBranchId == null) {
            throw new BadRequestException("Vui lòng chọn chi nhánh");
        }

        return requestedBranchId;
    }

    private UserModel findManagedTargetUser(Long targetUserId, UserModel actor) {
        if (actor == null) {
            throw new BadRequestException("Không xác định được người thực hiện");
        }

        UserModel target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản"));

        if (actor.getId() != null && actor.getId().equals(target.getId())) {
            throw new BadRequestException("Không thể thao tác trên chính tài khoản của mình");
        }

        assertCanManageTargetUser(actor, target);
        return target;
    }

    private void assertCanManageTargetUser(UserModel actor, UserModel target) {
        UserRole actorRole = actor.getRole();
        UserRole targetRole = target.getRole();
        if (actorRole == null || targetRole == null) {
            throw new ForbiddenException("Không xác định được quyền tài khoản");
        }

        UserRole webActorRole = actorRole.toWebRole();
        UserRole webTargetRole = targetRole.toWebRole();

        if (webActorRole == UserRole.ADMIN || webActorRole == UserRole.DIRECTOR) {
            return;
        }

        if (webActorRole == UserRole.BRANCH_MANAGER) {
            boolean manageableStaffRole = webTargetRole == UserRole.CASHIER
                    || webTargetRole == UserRole.INVENTORY_STAFF;
            boolean sameBranch = actor.getBranchId() != null
                    && actor.getBranchId().equals(target.getBranchId());
            if (manageableStaffRole && sameBranch) {
                return;
            }
        }

        throw new ForbiddenException("Không có quyền thao tác với tài khoản này");
    }

    private void clearManagedBranchIfNeeded(UserModel target) {
        if (target.getRole() != UserRole.BRANCH_MANAGER || target.getId() == null) {
            return;
        }

        branchRepository.findByManagerId(target.getId()).ifPresent(branch -> {
            branch.setManagerId(null);
            branchRepository.save(branch);
        });
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
}
