package base.api.feature.shiftsession.repository;

import base.api.shared.entity.ShiftSessionHighValueItemModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShiftSessionHighValueItemRepository extends JpaRepository<ShiftSessionHighValueItemModel, Long> {

    List<ShiftSessionHighValueItemModel> findBySessionIdOrderByIdAsc(Long sessionId);

    void deleteBySessionId(Long sessionId);
}
