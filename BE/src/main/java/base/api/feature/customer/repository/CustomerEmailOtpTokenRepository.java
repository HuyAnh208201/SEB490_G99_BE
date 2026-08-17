package base.api.feature.customer.repository;

import base.api.shared.entity.CustomerEmailOtpTokenModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerEmailOtpTokenRepository extends JpaRepository<CustomerEmailOtpTokenModel, Long> {
    Optional<CustomerEmailOtpTokenModel> findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String email, String purpose);
}
