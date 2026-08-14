package base.api.feature.customer.service;

import base.api.feature.auth.repository.IRoleRepository;
import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.customer.dto.CustomerAuthDtos;
import base.api.shared.config.JwtUtil;
import base.api.shared.entity.MembershipTierModel;
import base.api.shared.entity.RoleModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAuthServiceTest {

    @Mock
    private IUserRepository userRepository;
    @Mock
    private IRoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CustomerOtpService otpService;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private CustomerTierService tierService;

    private CustomerAuthService service;

    @BeforeEach
    void setUp() {
        service = new CustomerAuthService(
                userRepository,
                roleRepository,
                passwordEncoder,
                otpService,
                jwtUtil,
                tierService,
                new ObjectMapper());
    }

    @Test
    void loginPreservesMobileResponseAndCustomerJwtContract() {
        UserModel user = customer(7L, "customer@example.com", "0912345678");
        user.setFullName("Customer One");
        user.setPoints(2500L);
        MembershipTierModel tier = new MembershipTierModel();
        tier.setCode("GOLD");
        tier.setName("Gold");

        CustomerAuthDtos.LoginRequest request = new CustomerAuthDtos.LoginRequest();
        request.setIdentifier(" CUSTOMER@EXAMPLE.COM ");
        request.setPassword("Password@123");

        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password@123", user.getPassword())).thenReturn(true);
        when(tierService.syncUserTier(user)).thenReturn(tier);
        when(jwtUtil.generateCustomerToken(user)).thenReturn("mobile.jwt.token");

        CustomerAuthDtos.AuthResponse response = service.login(request);

        assertEquals("mobile.jwt.token", response.getToken());
        assertEquals(7L, response.getUserId());
        assertEquals("0912345678", response.getPhone());
        assertEquals("customer@example.com", response.getEmail());
        assertEquals("Customer One", response.getFullName());
        assertEquals(2500L, response.getPoints());
        assertEquals("GOLD", response.getTierCode());
        assertEquals("Gold", response.getTierName());
    }

    @Test
    void loginRejectsStaffAccountFromCustomerApi() {
        UserModel staff = customer(3L, "manager@example.com", "0912345678");
        RoleModel role = new RoleModel();
        role.setId(3L);
        role.setName(UserRole.BRANCH_MANAGER.name());
        staff.setRoleEntity(role);

        CustomerAuthDtos.LoginRequest request = new CustomerAuthDtos.LoginRequest();
        request.setIdentifier("manager@example.com");
        request.setPassword("Password@123");

        when(userRepository.findByEmail("manager@example.com")).thenReturn(Optional.of(staff));

        assertThrows(BadRequestException.class, () -> service.login(request));
        verify(jwtUtil, never()).generateCustomerToken(any());
    }

    @Test
    void verifyRegistrationCreatesCustomerOnlyAfterValidOtp() {
        CustomerAuthDtos.RegisterVerify request = new CustomerAuthDtos.RegisterVerify();
        request.setEmail("new@example.com");
        request.setOtp("123456");

        CustomerRegistrationPayload payload = new CustomerRegistrationPayload(
                "0912345678", "New Customer", "encoded-password");
        RoleModel customerRole = new RoleModel();
        customerRole.setId(7L);
        customerRole.setName(UserRole.CUSTOMER.name());

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("0912345678")).thenReturn(false);
        when(otpService.verifyRegistration("new@example.com", "123456")).thenReturn(payload);
        when(roleRepository.findByName(UserRole.CUSTOMER.name())).thenReturn(Optional.of(customerRole));
        when(userRepository.save(any(UserModel.class))).thenAnswer(invocation -> {
            UserModel saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });
        when(jwtUtil.generateCustomerToken(any(UserModel.class))).thenReturn("registered.jwt");

        CustomerAuthDtos.AuthResponse response = service.verifyRegister(request);

        assertEquals(11L, response.getUserId());
        assertEquals("registered.jwt", response.getToken());
        verify(otpService).verifyRegistration("new@example.com", "123456");
        verify(userRepository).save(any(UserModel.class));
    }

    private UserModel customer(Long id, String email, String phone) {
        RoleModel role = new RoleModel();
        role.setId(7L);
        role.setName(UserRole.CUSTOMER.name());
        UserModel user = new UserModel();
        user.setId(id);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword("encoded-password");
        user.setRoleEntity(role);
        user.setActive(true);
        user.setVerified(true);
        return user;
    }
}
