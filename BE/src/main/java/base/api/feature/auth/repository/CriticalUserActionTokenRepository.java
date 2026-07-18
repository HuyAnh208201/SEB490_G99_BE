package base.api.feature.auth.repository;

import base.api.shared.entity.CriticalUserActionTokenModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CriticalUserActionTokenRepository extends JpaRepository<CriticalUserActionTokenModel, Long> {

    Optional<CriticalUserActionTokenModel> findTopByTargetUserIdAndActorUserIdAndActionTypeAndUsedFalseOrderByCreatedAtDesc(
            Long targetUserId,
            Long actorUserId,
            String actionType
    );
}
