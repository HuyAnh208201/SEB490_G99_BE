package base.api.shared.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * JWTs invalidated by logout.
 *
 * token_hash is both the primary key and the only thing ever looked up, so there is no
 * generated id column: adding one would only build a second index for the same key.
 * Stores SHA-256 hex rather than the raw token — see TokenHasher.
 */
@Data
@Entity
@Table(name = "revoked_tokens", indexes = {
        @Index(name = "idx_revoked_tokens_expires_at", columnList = "expires_at")
})
public class RevokedTokenModel {

    @Id
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime revokedAt;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
