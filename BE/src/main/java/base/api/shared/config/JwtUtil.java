package base.api.shared.config;

import base.api.shared.entity.UserModel;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;

    @Value("${customer.jwt.expiration-ms:360000000}")
    private long customerExpirationMs;

    public JwtUtil() {
        String secret = "MyVerySecretKeyThatIsLongEnough123456789";
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UserModel userModel) {
        return Jwts.builder()
                .setSubject(userModel.getUserName())
                .claim("email", userModel.getEmail())
                .claim("user_name", userModel.getUserName())
                .claim("role", userModel.getRole().name())
                .claim("id", userModel.getId())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 100))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Keeps the token claims used by the existing customer mobile client. */
    public String generateCustomerToken(UserModel userModel) {
        return Jwts.builder()
                .setSubject(userModel.getPhone())
                .claim("id", userModel.getId())
                .claim("phone", userModel.getPhone())
                .claim("fullName", userModel.getFullName())
                .claim("role", "CUSTOMER")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + customerExpirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private Claims parseAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractUsername(String token) {
        return parseAllClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Claims claims = parseAllClaims(token);
        return claims.get("id", Long.class);
    }

    public Date extractExpiration(String token) {
        return parseAllClaims(token).getExpiration();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())
                && !parseAllClaims(token).getExpiration().before(new Date()));
    }

    public boolean isTokenValidForUserId(String token, Long userId) {
        Claims claims = parseAllClaims(token);
        Long tokenUserId = claims.get("id", Long.class);
        return userId != null
                && userId.equals(tokenUserId)
                && !claims.getExpiration().before(new Date());
    }

}
