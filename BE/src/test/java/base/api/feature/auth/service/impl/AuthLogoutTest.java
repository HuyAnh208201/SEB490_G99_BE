package base.api.feature.auth.service.impl;

import base.api.feature.auth.repository.IRevokedTokenRepository;
import base.api.feature.auth.service.RevokedTokenCache;
import base.api.feature.auth.service.IUserService;
import base.api.shared.config.JwtUtil;
import base.api.shared.entity.RevokedTokenModel;
import base.api.shared.util.TokenHasher;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthServiceImpl#logout} — blank/null token rejection and token revocation.
 */
@ExtendWith(MockitoExtension.class)
class AuthLogoutTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private IUserService userService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private IRevokedTokenRepository revokedTokenRepository;

    @Mock
    private RevokedTokenCache revokedTokenCache;

    @InjectMocks
    private AuthServiceImpl service;

    @Test
    void logoutRejectsNullToken() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> service.logout(null));

        assertTrue(error.getMessage().contains("Token không hợp lệ"));
        verify(jwtUtil, never()).extractExpiration(any());
        verify(revokedTokenRepository, never()).save(any());
    }

    @Test
    void logoutRejectsBlankToken() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> service.logout("   "));

        assertTrue(error.getMessage().contains("Token không hợp lệ"));
        verify(jwtUtil, never()).extractExpiration(any());
        verify(revokedTokenRepository, never()).save(any());
    }

    @Test
    void logoutRevokesHashedTokenWithItsExpiry() {
        Date expiration = new Date(System.currentTimeMillis() + 3_600_000L);
        when(jwtUtil.extractExpiration("jwt-token")).thenReturn(expiration);

        service.logout("jwt-token");

        ArgumentCaptor<RevokedTokenModel> captor = ArgumentCaptor.forClass(RevokedTokenModel.class);
        verify(revokedTokenRepository).save(captor.capture());
        RevokedTokenModel saved = captor.getValue();
        assertEquals(TokenHasher.sha256("jwt-token"), saved.getTokenHash());
        assertEquals(
                LocalDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault()),
                saved.getExpiresAt());
        // revoked_at is NOT NULL and @PrePersist does not run on the merge path, so the
        // service must set it — without that a second logout breaks the constraint.
        assertNotNull(saved.getRevokedAt());
    }

    @Test
    void logoutTwiceRewritesSameRowInsteadOfFailing() {
        when(jwtUtil.extractExpiration("jwt-token"))
                .thenReturn(new Date(System.currentTimeMillis() + 3_600_000L));

        service.logout("jwt-token");
        service.logout("jwt-token");

        // token_hash is an assigned primary key, so save() is an upsert and cannot collide.
        ArgumentCaptor<RevokedTokenModel> captor = ArgumentCaptor.forClass(RevokedTokenModel.class);
        verify(revokedTokenRepository, times(2)).save(captor.capture());
        assertEquals(
                captor.getAllValues().get(0).getTokenHash(),
                captor.getAllValues().get(1).getTokenHash());
    }

    @Test
    void logoutWritesDbBeforeCache() {
        Date expiration = new Date(System.currentTimeMillis() + 3_600_000L);
        when(jwtUtil.extractExpiration("jwt-token")).thenReturn(expiration);

        service.logout("jwt-token");

        // Caching first and then failing the write would report success while a restart loses
        // it — the token returns. Writing the database first surfaces the failure as a failure.
        InOrder inOrder = inOrder(revokedTokenRepository, revokedTokenCache);
        inOrder.verify(revokedTokenRepository).save(any(RevokedTokenModel.class));
        inOrder.verify(revokedTokenCache).remember(
                TokenHasher.sha256("jwt-token"),
                LocalDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault()));
    }

    @Test
    void logoutDoesNotCacheWhenDbWriteFails() {
        when(jwtUtil.extractExpiration("jwt-token"))
                .thenReturn(new Date(System.currentTimeMillis() + 3_600_000L));
        when(revokedTokenRepository.save(any(RevokedTokenModel.class)))
                .thenThrow(new DataIntegrityViolationException("db down"));

        assertThrows(DataIntegrityViolationException.class, () -> service.logout("jwt-token"));

        verify(revokedTokenCache, never()).remember(any(), any());
    }

    @Test
    void logoutIgnoresMalformedTokenWithoutRevoking() {
        when(jwtUtil.extractExpiration("broken")).thenThrow(new MalformedJwtException("bad"));

        service.logout("broken");

        verify(revokedTokenRepository, never()).save(any());
    }
}
