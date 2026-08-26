package base.api.feature.shiftsession.repository;

import base.api.shared.entity.ShiftSessionApprovalModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShiftSessionApprovalRepository extends JpaRepository<ShiftSessionApprovalModel, Long> {

    List<ShiftSessionApprovalModel> findBySessionIdOrderByDecidedAtDesc(Long sessionId);

    void deleteBySessionId(Long sessionId);
}
