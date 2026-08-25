package base.api.feature.shiftsession.repository;

import base.api.shared.entity.ShiftSessionModel;
import base.api.shared.enums.ShiftSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftSessionRepository extends JpaRepository<ShiftSessionModel, Long> {

    Optional<ShiftSessionModel> findFirstByEmployeeIdAndStatusInOrderByOpenedAtDesc(
            Long employeeId,
            List<ShiftSessionStatus> statuses);

    Optional<ShiftSessionModel> findFirstByShiftIdAndEmployeeIdOrderByIdDesc(
            Long shiftId,
            Long employeeId);

    Optional<ShiftSessionModel> findFirstByShiftIdAndStatusAndEmployeeIdNotOrderByOpenedAtAsc(
            Long shiftId,
            ShiftSessionStatus status,
            Long employeeId);

    List<ShiftSessionModel> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    org.springframework.data.domain.Page<ShiftSessionModel> findByEmployeeId(
            Long employeeId,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT s FROM ShiftSessionModel s, ShiftModel sh
            WHERE s.shiftId = sh.id
              AND s.employeeId = :employeeId
              AND (sh.startTime IS NULL OR sh.startTime <= :now)
            """)
    org.springframework.data.domain.Page<ShiftSessionModel> findPastHistoryByEmployeeId(
            @Param("employeeId") Long employeeId,
            @Param("now") LocalDateTime now,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT s FROM ShiftSessionModel s, ShiftModel sh
            WHERE s.shiftId = sh.id
              AND s.status IN :statuses
              AND sh.startTime IS NOT NULL
              AND sh.startTime > :now
            """)
    List<ShiftSessionModel> findFutureClosedSessions(
            @Param("statuses") List<ShiftSessionStatus> statuses,
            @Param("now") LocalDateTime now);

    Optional<ShiftSessionModel> findFirstByBranchIdAndStatusAndRoleOrderByClosedAtDesc(
            Long branchId,
            ShiftSessionStatus status,
            base.api.shared.enums.UserRole role);

    Optional<ShiftSessionModel> findFirstByBranchIdAndStatusOrderByOpenedAtDesc(
            Long branchId,
            ShiftSessionStatus status);

    Optional<ShiftSessionModel> findFirstByBranchIdAndStatusInAndRoleOrderByClosedAtDesc(
            Long branchId,
            List<ShiftSessionStatus> statuses,
            base.api.shared.enums.UserRole role);

    List<ShiftSessionModel> findByBranchIdAndStatusInOrderByOpenedAtDesc(
            Long branchId,
            List<ShiftSessionStatus> statuses);

    List<ShiftSessionModel> findByBranchIdAndStatusOrderByClosedAtDesc(
            Long branchId,
            ShiftSessionStatus status);

    @Query("""
            SELECT DISTINCT s FROM ShiftSessionModel s
            LEFT JOIN ShiftSessionHighValueItemModel h ON h.sessionId = s.id
            WHERE s.branchId = :branchId
              AND s.status = :status
              AND (
                (s.difference IS NOT NULL AND s.difference <> 0)
                OR (h.difference IS NOT NULL AND h.difference <> 0)
              )
            ORDER BY s.closedAt DESC
            """)
    List<ShiftSessionModel> findPendingReconciliationWithDifference(
            @Param("branchId") Long branchId,
            @Param("status") ShiftSessionStatus status);

    @Query("""
            SELECT DISTINCT s FROM ShiftSessionModel s
            WHERE s.branchId = :branchId
              AND s.status IN :statuses
              AND (s.difference IS NULL OR s.difference = 0)
              AND s.id NOT IN (
                SELECT h.sessionId FROM ShiftSessionHighValueItemModel h
                WHERE h.difference IS NOT NULL AND h.difference <> 0
              )
              AND s.closedAt IS NOT NULL
              AND s.closedAt >= :since
            ORDER BY s.closedAt DESC
            """)
    List<ShiftSessionModel> findBalancedClosedSessions(
            @Param("branchId") Long branchId,
            @Param("statuses") List<ShiftSessionStatus> statuses,
            @Param("since") LocalDateTime since);

    @Query("""
            SELECT COUNT(DISTINCT s.id) FROM ShiftSessionModel s
            LEFT JOIN ShiftSessionHighValueItemModel h ON h.sessionId = s.id
            WHERE s.branchId = :branchId
              AND s.status = :status
              AND (
                (s.difference IS NOT NULL AND s.difference <> 0)
                OR (h.difference IS NOT NULL AND h.difference <> 0)
              )
            """)
    long countPendingReconciliationWithDifference(
            @Param("branchId") Long branchId,
            @Param("status") ShiftSessionStatus status);

    Optional<ShiftSessionModel> findFirstByEmployeeIdAndStatusOrderByClosedAtDesc(
            Long employeeId,
            ShiftSessionStatus status);

    long countByBranchIdAndStatus(Long branchId, ShiftSessionStatus status);

    @Query("""
            SELECT s FROM ShiftSessionModel s, ShiftModel sh
            WHERE s.shiftId = sh.id
              AND s.status IN :statuses
              AND sh.endTime IS NOT NULL
              AND sh.endTime < :cutoff
            """)
    List<ShiftSessionModel> findOverdueCashierSessions(
            @Param("statuses") List<ShiftSessionStatus> statuses,
            @Param("cutoff") LocalDateTime cutoff);
}

