package base.api.shared.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Blacklist JWT sau khi logout.
 *
 * token_hash vừa là khóa chính vừa là thứ duy nhất cần tra cứu, nên không dùng
 * cột id sinh tự động: thêm id chỉ tạo ra index thứ hai cho cùng một khóa logic.
 * Lưu SHA-256 hex thay vì token thô — xem TokenHasher.
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
