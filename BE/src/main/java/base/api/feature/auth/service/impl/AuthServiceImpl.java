package base.api.feature.auth.service.impl;

import base.api.feature.auth.dto.request.AuthRequest;
import base.api.feature.auth.dto.response.AuthResponse;
import base.api.feature.auth.repository.IRevokedTokenRepository;
import base.api.feature.auth.service.IAuthService;
import base.api.feature.auth.service.RevokedTokenCache;
import base.api.feature.auth.service.IUserService;
import base.api.shared.config.JwtUtil;
import base.api.shared.entity.RevokedTokenModel;
import base.api.shared.entity.UserModel;
import base.api.shared.util.TokenHasher;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
public class AuthServiceImpl implements IAuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private IUserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private IRevokedTokenRepository revokedTokenRepository;

    @Autowired
    private RevokedTokenCache revokedTokenCache;

    @Override
    public AuthResponse login(AuthRequest dto) {
        String normalizedLogin = normalizeLogin(dto.getUsername());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedLogin, dto.getPassword())
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("Sai tên đăng nhập hoặc mật khẩu");
        }

        UserModel user = userService.findByUserName(normalizedLogin);
        if (user == null) {
            throw new IllegalArgumentException("Không tìm thấy user");
        }
        if (!user.isVerified()) {
            throw new IllegalArgumentException("Vui lòng xác thực email trước khi đăng nhập");
        }
        if (!user.isActive()) {
            throw new IllegalArgumentException("Tài khoản đã bị vô hiệu hóa");
        }
        if (!user.getRole().toWebRole().isWebRole()) {
            throw new IllegalArgumentException("Tài khoản không có quyền truy cập Web System");
        }

        AuthResponse authResponse = new AuthResponse();
        authResponse.setAccessToken(jwtUtil.generateToken(user));
        return authResponse;
    }

    @Override
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token không hợp lệ");
        }

        Date expiration;
        try {
            expiration = jwtUtil.extractExpiration(token);
        } catch (JwtException ex) {
            // A malformed or expired token needs no revocation; the filter already refuses it.
            return;
        }

        String tokenHash = TokenHasher.sha256(token);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault());

        // token_hash is an assigned primary key, so save() is an upsert: logging out twice,
        // or two concurrent requests, both overwrite one row instead of colliding.
        RevokedTokenModel revoked = new RevokedTokenModel();
        revoked.setTokenHash(tokenHash);
        revoked.setExpiresAt(expiresAt);
        revoked.setRevokedAt(LocalDateTime.now());
        revokedTokenRepository.save(revoked);

        // Write the database first, then the cache: if the write fails we must not report
        // success, because a restart reloads the cache from the table and the token returns.
        revokedTokenCache.remember(tokenHash, expiresAt);
    }

    private String normalizeLogin(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toLowerCase();
    }

}
