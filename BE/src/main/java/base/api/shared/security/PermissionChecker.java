package base.api.shared.security;

import base.api.shared.enums.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Bean SpEL cho {@code @PreAuthorize("@permissionChecker.has('USER_MANAGEMENT_LIST')")}.
 */
@Component("permissionChecker")
public class PermissionChecker {

    public boolean has(String permissionName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        UserRole role = resolveRole(authentication);
        if (role == null) {
            return false;
        }

        try {
            WebPermission permission = WebPermission.valueOf(permissionName);
            return WebRolePermissions.isAllowed(role, permission);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean hasAny(String... permissionNames) {
        for (String name : permissionNames) {
            if (has(name)) {
                return true;
            }
        }
        return false;
    }

    private UserRole resolveRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .findFirst()
                .map(authority -> authority.substring("ROLE_".length()))
                .map(this::parseRole)
                .map(UserRole::toWebRole)
                .orElse(null);
    }

    private UserRole parseRole(String roleName) {
        try {
            return UserRole.valueOf(roleName);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
