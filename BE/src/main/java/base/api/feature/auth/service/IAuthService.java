package base.api.feature.auth.service;

import base.api.feature.auth.dto.request.AuthRequest;
import base.api.feature.auth.dto.response.AuthResponse;

public interface IAuthService {
    AuthResponse login(AuthRequest dto);

    void logout(String token);
}
