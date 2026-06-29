package base.api.shared.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationTokenModel {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String verificationToken;

    /**
     * Cột tồn tại trên DB (NOT NULL). Link xác thực dùng {@link #verificationToken}; giữ cùng giá trị để thỏa constraint.
     */
    @Column(name = "verification_code", nullable = false, length = 255)
    private String verificationCode;

    @Column(nullable = false)
    private String email;
    
    @Column(nullable = false)
    private Long userId;
    
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    private boolean used = false;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
