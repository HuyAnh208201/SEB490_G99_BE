package base.api.feature.auth.service.impl;

import base.api.feature.auth.dto.request.AuthRequest;
import base.api.feature.auth.dto.response.AuthResponse;
import base.api.feature.auth.service.IAuthService;
import base.api.feature.auth.service.IUserService;
import base.api.shared.config.JwtUtil;
import base.api.shared.entity.UserModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements IAuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private IUserService userService;

    @Autowired
    private JwtUtil jwtUtil;

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

        jwtUtil.extractExpiration(token);
    }

    private String normalizeLogin(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toLowerCase();
    }

}
