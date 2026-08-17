package base.api.shared.security;

import base.api.feature.auth.repository.IUserRepository;
import base.api.shared.config.JwtUtil;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CurrentUserProvider {

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private IUserRepository userRepository;

    public UserModel getCurrentUserOrThrow() {
        Long userId = extractUserIdFromRequest();
        if (userId != null) {
            return userRepository.findById(userId)
                    .orElseThrow(() -> new ForbiddenException("Access denied."));
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new ForbiddenException("Access denied.");
        }

        return userRepository.findByUserName(authentication.getName())
                .or(() -> userRepository.findByUserNameOrEmail(authentication.getName()))
                .or(() -> userRepository.findByPhone(authentication.getName()))
                .orElseThrow(() -> new ForbiddenException("Access denied."));
    }

    public UserRole getCurrentUserRole() {
        return resolveRoleFromSecurityContext()
                .orElseGet(() -> {
                    UserModel user = getCurrentUserOrThrow();
                    if (user.getRole() == null) {
                        throw new ForbiddenException("Access denied.");
                    }
                    return user.getRole().toWebRole();
                });
    }

    private Optional<UserRole> resolveRoleFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .findFirst()
                .map(authority -> authority.substring("ROLE_".length()))
                .map(this::parseRole)
                .map(UserRole::toWebRole);
    }

    private UserRole parseRole(String roleName) {
        try {
            return UserRole.valueOf(roleName);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Long extractUserIdFromRequest() {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            return jwtUtil.extractUserId(authHeader.substring(7));
        } catch (Exception ex) {
            return null;
        }
    }
}
