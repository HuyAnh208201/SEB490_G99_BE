package base.api.feature.shift.repository;

import base.api.shared.entity.ShiftModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ShiftRepository extends JpaRepository<ShiftModel, Long> {

    List<ShiftModel> findByBranchIdOrderByStartTimeAsc(Long branchId);

    List<ShiftModel> findByBranchIdAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAsc(
            Long branchId,
            LocalDateTime endTime,
            LocalDateTime startTime);
}
