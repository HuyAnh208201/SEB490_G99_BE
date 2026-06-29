package base.api.feature.auth.repository;

import base.api.shared.entity.EmailVerificationTokenModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IEmailVerificationTokenRepository extends JpaRepository<EmailVerificationTokenModel, Long> {
    Optional<EmailVerificationTokenModel> findByVerificationToken(String verificationToken);
    Optional<EmailVerificationTokenModel> findByEmail(String email);
    void deleteByEmail(String email);
}
