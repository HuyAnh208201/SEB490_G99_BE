package base.api.feature.auth.service.impl;

import base.api.feature.auth.dto.request.AuthRequest;
import base.api.feature.auth.dto.response.AuthResponse;
import base.api.feature.auth.service.IUserService;
import base.api.shared.config.JwtUtil;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthServiceImpl#login} — decision coverage for auth fail,
 * missing user, unverified, inactive, non-web role, and successful JWT issue.
 */
@ExtendWith(MockitoExtension.class)
class AuthLoginTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private IUserService userService;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthServiceImpl service;

    @Test
    void loginReturnsJwtWhenCredentialsAndAccountAreValid() {
        AuthRequest request = request("cashier01", "Secret123");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);
        UserModel user = webUser("cashier01", UserRole.CASHIER, true, true);
        when(userService.findByUserName("cashier01")).thenReturn(user);
        when(jwtUtil.generateToken(user)).thenReturn("jwt-token");

        AuthResponse response = service.login(request);

        assertEquals("jwt-token", response.getAccessToken());
        verify(jwtUtil).generateToken(user);
    }

    @Test
    void loginNormalizesUsernameBeforeAuthenticate() {
        AuthRequest request = request("  Cashier01  ", "Secret123");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);
        UserModel user = webUser("cashier01", UserRole.CASHIER, true, true);
        when(userService.findByUserName("cashier01")).thenReturn(user);
        when(jwtUtil.generateToken(user)).thenReturn("jwt-token");

        service.login(request);

        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("cashier01", "Secret123"));
        verify(userService).findByUserName("cashier01");
    }

    @Test
    void loginRejectsWrongPassword() {
        AuthRequest request = request("cashier01", "wrong");
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> service.login(request));

        assertTrue(error.getMessage().contains("Sai tên đăng nhập hoặc mật khẩu"));
        verify(userService, never()).findByUserName(any());
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void loginRejectsWhenUserNotFoundAfterAuth() {
        AuthRequest request = request("ghost", "Secret123");
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userService.findByUserName("ghost")).thenReturn(null);

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> service.login(request));

        assertTrue(error.getMessage().contains("Không tìm thấy user"));
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void loginRejectsUnverifiedAccount() {
        AuthRequest request = request("newuser", "Secret123");
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userService.findByUserName("newuser"))
                .thenReturn(webUser("newuser", UserRole.CASHIER, false, true));

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> service.login(request));

        assertTrue(error.getMessage().contains("xác thực email"));
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void loginRejectsInactiveAccount() {
        AuthRequest request = request("disabled", "Secret123");
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userService.findByUserName("disabled"))
                .thenReturn(webUser("disabled", UserRole.CASHIER, true, false));

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> service.login(request));

        assertTrue(error.getMessage().contains("vô hiệu hóa"));
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void loginRejectsNonWebRole() {
        AuthRequest request = request("customer01", "Secret123");
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userService.findByUserName("customer01"))
                .thenReturn(webUser("customer01", UserRole.CUSTOMER, true, true));

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> service.login(request));

        assertTrue(error.getMessage().contains("Web System"));
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void loginSucceedsForBranchManagerWebRole() {
        AuthRequest request = request("bm01", "Secret123");
        when(authenticationManager.authenticate(any())).thenReturn(null);
        UserModel user = webUser("bm01", UserRole.BRANCH_MANAGER, true, true);
        when(userService.findByUserName("bm01")).thenReturn(user);
        when(jwtUtil.generateToken(user)).thenReturn("bm-jwt");

        AuthResponse response = service.login(request);

        assertNotNull(response.getAccessToken());
        assertEquals("bm-jwt", response.getAccessToken());
    }

    private static AuthRequest request(String username, String password) {
        AuthRequest request = new AuthRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private static UserModel webUser(String username, UserRole role, boolean verified, boolean active) {
        UserModel user = new UserModel();
        user.setId(1L);
        user.setUserName(username);
        user.setEmail(username + "@chainstore.com");
        user.setRole(role);
        user.setVerified(verified);
        user.setActive(active);
        return user;
    }
}
