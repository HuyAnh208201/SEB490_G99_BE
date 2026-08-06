package base.api.feature.shift.repository;

import base.api.shared.entity.ShiftModel;
import base.api.shared.enums.ShiftStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftRepository extends JpaRepository<ShiftModel, Long> {

    List<ShiftModel> findByBranchIdOrderByStartTimeAsc(Long branchId);

    List<ShiftModel> findByBranchIdAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAsc(
            Long branchId,
            LocalDateTime endTime,
            LocalDateTime startTime);

    List<ShiftModel> findByBranchIdAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
            Long branchId,
            LocalDateTime startTime,
            LocalDateTime endTime);

    @Query("""
            SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
            FROM ShiftModel s
            WHERE s.branchId = :branchId
              AND s.startTime < :endTime
              AND s.endTime > :startTime
              AND (:excludedShiftId IS NULL OR s.id <> :excludedShiftId)
            """)
    boolean existsOverlapping(
            @Param("branchId") Long branchId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("excludedShiftId") Long excludedShiftId);

    Optional<ShiftModel> findByBranchIdAndStartTimeAndEndTime(
            Long branchId,
            LocalDateTime startTime,
            LocalDateTime endTime);

    long countByBranchIdAndStatus(Long branchId, ShiftStatus status);
}
