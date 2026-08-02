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
            throw new IllegalArgumentException("Incorrect username or password");
        }

        UserModel user = userService.findByUserName(normalizedLogin);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        if (!user.isVerified()) {
            throw new IllegalArgumentException("Please verify your email before signing in");
        }
        if (!user.isActive()) {
            throw new IllegalArgumentException("Account has been disabled");
        }
        if (!user.getRole().toWebRole().isWebRole()) {
            throw new IllegalArgumentException("Account does not have access to the Web System");
        }

        AuthResponse authResponse = new AuthResponse();
        authResponse.setAccessToken(jwtUtil.generateToken(user));
        return authResponse;
    }

    @Override
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Invalid token");
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
