package base.api.feature.auth.repository;

import base.api.shared.entity.RevokedTokenModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface IRevokedTokenRepository extends JpaRepository<RevokedTokenModel, Long> {

    boolean existsByToken(String token);

    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}
