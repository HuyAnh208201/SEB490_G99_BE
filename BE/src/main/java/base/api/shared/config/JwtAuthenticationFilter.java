package base.api.shared.config;

import base.api.feature.auth.service.RevokedTokenCache;
import base.api.feature.auth.service.impl.CustomUserDetailsService;
import base.api.shared.util.TokenHasher;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Component
public class JwtAuthenticationFilter  extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private RevokedTokenCache revokedTokenCache;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        boolean isPublic = Arrays.stream(SecurityConfig.PUBLIC_ENDPOINTS)
                .anyMatch(pattern -> pathMatcher.match(pattern, path));

        if (isPublic) {
            filterChain.doFilter(request, response);
            return;
        }

        final String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            if (isRevoked(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Token revoked\"}");
                return;
            }

            String username = jwtUtil.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                var userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtUtil.isTokenValid(token, userDetails)) {
                    if (!userDetails.isEnabled()) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"message\":\"Account deactivated\"}");
                        return;
                    }
                    var auth = UsernamePasswordAuthenticationToken.authenticated(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (ExpiredJwtException ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Token expired\"}");
            return;
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Token processing failed\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Tra cache trong RAM, không chạm DB — xem RevokedTokenCache để biết vì sao.
     *
     * Cache rỗng khi không nạp được (bảng chưa tạo / DB lỗi) nên hành vi ở đây là
     * fail-open có chủ đích: tạm mất khả năng thu hồi token, đúng bằng hành vi cũ
     * trước khi có tính năng này. Nếu ném lỗi thì rơi vào catch(Exception) phía
     * trên và mọi request có token đều bị 401 — cả hệ thống chết. RevokedTokenCache
     * đã log ERROR khi nạp hỏng.
     */
    private boolean isRevoked(String token) {
        return revokedTokenCache.isRevoked(TokenHasher.sha256(token));
    }

}
