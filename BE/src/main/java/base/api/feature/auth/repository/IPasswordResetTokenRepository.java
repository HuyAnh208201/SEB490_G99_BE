package base.api.feature.auth.repository;

import base.api.shared.entity.PasswordResetTokenModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IPasswordResetTokenRepository extends JpaRepository<PasswordResetTokenModel, Long> {
    Optional<PasswordResetTokenModel> findByResetToken(String resetToken);
    Optional<PasswordResetTokenModel> findByEmail(String email);
    void deleteByEmail(String email);
}
