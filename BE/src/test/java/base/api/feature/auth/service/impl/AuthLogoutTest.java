package base.api.feature.auth.service.impl;

import base.api.feature.auth.service.IUserService;
import base.api.shared.config.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthServiceImpl#logout} — blank/null token rejection and valid token parse.
 */
@ExtendWith(MockitoExtension.class)
class AuthLogoutTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private IUserService userService;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthServiceImpl service;

    @Test
    void logoutRejectsNullToken() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> service.logout(null));

        assertTrue(error.getMessage().contains("Token không hợp lệ"));
        verify(jwtUtil, never()).extractExpiration(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void logoutRejectsBlankToken() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> service.logout("   "));

        assertTrue(error.getMessage().contains("Token không hợp lệ"));
        verify(jwtUtil, never()).extractExpiration(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void logoutAcceptsNonBlankToken() {
        when(jwtUtil.extractExpiration("jwt-token")).thenReturn(new Date());

        service.logout("jwt-token");

        verify(jwtUtil).extractExpiration("jwt-token");
    }
}
