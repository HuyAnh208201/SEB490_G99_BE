package base.api.feature.customer.service;

import base.api.feature.auth.repository.IRoleRepository;
import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.customer.dto.CustomerAuthDtos;
import base.api.feature.customer.util.CustomerEmailNormalizer;
import base.api.feature.customer.util.CustomerPhoneNormalizer;
import base.api.shared.config.JwtUtil;
import base.api.shared.entity.MembershipTierModel;
import base.api.shared.entity.RoleModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerAuthService {

    private static final String FORGOT_GENERIC_MESSAGE =
            "If an account exists for this email, a verification code has been sent";

    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomerOtpService otpService;
    private final JwtUtil jwtUtil;
    private final CustomerTierService tierService;

    public CustomerAuthService(
            IUserRepository userRepository,
            IRoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            CustomerOtpService otpService,
            JwtUtil jwtUtil,
            CustomerTierService tierService,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.jwtUtil = jwtUtil;
        this.tierService = tierService;
    }

    @Transactional
    public CustomerAuthDtos.MessageResponse requestRegisterOtp(CustomerAuthDtos.RegisterRequestOtp request) {
        String phone = requireValidPhone(request.getPhone());
        String email = requireValidEmail(request.getEmail());
        if (userRepository.existsByPhone(phone)) {
            throw new BadRequestException("Phone number is already registered");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email is already registered");
        }
        CustomerRegistrationPayload payload = new CustomerRegistrationPayload(
                phone,
                request.getFullName().trim(),
                passwordEncoder.encode(request.getPassword()));
        otpService.issueRegistration(email, payload);
        return CustomerAuthDtos.MessageResponse.of("Verification code sent to your email");
    }

    @Transactional
    public CustomerAuthDtos.AuthResponse verifyRegister(CustomerAuthDtos.RegisterVerify request) {
        String email = requireValidEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email is already registered");
        }
        CustomerRegistrationPayload payload = otpService.verifyRegistration(email, request.getOtp());
        if (userRepository.existsByPhone(payload.phone())) {
            throw new BadRequestException("Phone number is already registered");
        }
        RoleModel customerRole = roleRepository.findByName(UserRole.CUSTOMER.name())
                .orElseThrow(() -> new NotFoundException("Customer role is not configured"));

        UserModel user = new UserModel();
        user.setPhone(payload.phone());
        user.setEmail(email);
        user.setFullName(payload.fullName());
        user.setPassword(payload.passwordHash());
        user.setRoleEntity(customerRole);
        user.setActive(true);
        user.setVerified(true);
        user.setPoints(0L);
        user = userRepository.save(user);
        return toAuthResponse(user, tierService.syncUserTier(user));
    }

    @Transactional
    public CustomerAuthDtos.AuthResponse login(CustomerAuthDtos.LoginRequest request) {
        String raw = request.resolvedIdentifier();
        if (raw == null || raw.isBlank()) {
            throw invalidCredentials();
        }
        UserModel user;
        if (raw.contains("@")) {
            user = userRepository.findByEmail(CustomerEmailNormalizer.normalize(raw))
                    .orElseThrow(this::invalidCredentials);
        } else {
            String phone = CustomerPhoneNormalizer.normalize(raw);
            if (!CustomerPhoneNormalizer.isValidVnMobile(phone)) {
                throw invalidCredentials();
            }
            user = userRepository.findByPhone(phone).orElseThrow(this::invalidCredentials);
        }
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new BadRequestException("This account cannot access the customer app");
        }
        if (!user.isActive()) {
            throw new BadRequestException("Account is not active");
        }
        if (!user.isVerified()) {
            throw new BadRequestException("Account is not verified");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw invalidCredentials();
        }
        return toAuthResponse(user, tierService.syncUserTier(user));
    }

    @Transactional
    public CustomerAuthDtos.MessageResponse requestForgotOtp(CustomerAuthDtos.ForgotRequestOtp request) {
        String email = CustomerEmailNormalizer.normalize(request.getEmail());
        if (!CustomerEmailNormalizer.isValid(email)) {
            return CustomerAuthDtos.MessageResponse.of(FORGOT_GENERIC_MESSAGE);
        }
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getRole() == UserRole.CUSTOMER && user.isActive()) {
                otpService.issuePasswordReset(email);
            }
        });
        return CustomerAuthDtos.MessageResponse.of(FORGOT_GENERIC_MESSAGE);
    }

    @Transactional
    public CustomerAuthDtos.MessageResponse verifyForgot(CustomerAuthDtos.ForgotVerify request) {
        String email = requireValidEmail(request.getEmail());
        UserModel user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Invalid email or verification code"));
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new BadRequestException(
                    "This email belongs to a staff account and cannot reset password in the customer app");
        }
        if (!user.isActive()) {
            throw new BadRequestException("Account is not active");
        }
        otpService.verifyPasswordReset(email, request.getOtp());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        return CustomerAuthDtos.MessageResponse.of("Password updated successfully");
    }

    private CustomerAuthDtos.AuthResponse toAuthResponse(UserModel user, MembershipTierModel tier) {
        CustomerAuthDtos.AuthResponse response = new CustomerAuthDtos.AuthResponse();
        response.setToken(jwtUtil.generateCustomerToken(user));
        response.setUserId(user.getId());
        response.setPhone(user.getPhone());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setPoints(user.getPoints());
        if (tier != null) {
            response.setTierCode(tier.getCode());
            response.setTierName(tier.getName());
        }
        return response;
    }

    private String requireValidEmail(String raw) {
        String email = CustomerEmailNormalizer.normalize(raw);
        if (!CustomerEmailNormalizer.isValid(email)) {
            throw new BadRequestException("Invalid email address");
        }
        return email;
    }

    private String requireValidPhone(String raw) {
        String phone = CustomerPhoneNormalizer.normalize(raw);
        if (!CustomerPhoneNormalizer.isValidVnMobile(phone)) {
            throw new BadRequestException("Invalid phone number");
        }
        return phone;
    }

    private BadRequestException invalidCredentials() {
        return new BadRequestException("Invalid email/phone or password");
    }
}
