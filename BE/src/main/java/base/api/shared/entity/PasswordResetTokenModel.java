package base.api.shared.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetTokenModel {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String resetToken;
    
    @Column(nullable = false)
    private String email;
    
    @Column(nullable = false)
    private Long userId;
    
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;

    private boolean used = false;

    @Column(name = "verification_code", nullable = false)
    private String verificationCode;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (verificationCode == null || verificationCode.isBlank()) {
            verificationCode = java.util.UUID.randomUUID().toString();
        }
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
