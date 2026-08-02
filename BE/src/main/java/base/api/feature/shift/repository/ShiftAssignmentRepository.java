package base.api.feature.shift.repository;

import base.api.shared.entity.ShiftAssignmentModel;
import base.api.shared.enums.ShiftStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignmentModel, Long> {

    @Query("""
            SELECT a FROM ShiftAssignmentModel a
            JOIN FETCH a.shift s
            JOIN FETCH a.staff st
            WHERE s.id = :shiftId
            """)
    List<ShiftAssignmentModel> findByShiftId(@Param("shiftId") Long shiftId);

    @Query("""
            SELECT a FROM ShiftAssignmentModel a
            JOIN FETCH a.shift s
            JOIN FETCH a.staff st
            WHERE s.id IN :shiftIds
            """)
    List<ShiftAssignmentModel> findByShiftIdIn(@Param("shiftIds") Collection<Long> shiftIds);

    Optional<ShiftAssignmentModel> findFirstByShiftIdAndStaffIdOrderByIdDesc(Long shiftId, Long staffId);

    Optional<ShiftAssignmentModel> findByShiftIdAndStaffId(Long shiftId, Long staffId);

    boolean existsByShiftIdAndStaffId(Long shiftId, Long staffId);

    void deleteByShiftIdAndStaffId(Long shiftId, Long staffId);

    @Modifying
    @Query("DELETE FROM ShiftAssignmentModel a WHERE a.shift.id = :shiftId")
    int deleteAllByShiftId(@Param("shiftId") Long shiftId);

    @Query("""
            SELECT a FROM ShiftAssignmentModel a
            JOIN FETCH a.shift s
            JOIN FETCH a.staff st
            WHERE st.id = :staffId
              AND s.startTime < :endTime
              AND s.endTime > :startTime
            """)
    List<ShiftAssignmentModel> findOverlappingAssignments(
            @Param("staffId") Long staffId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("""
            SELECT a FROM ShiftAssignmentModel a
            JOIN FETCH a.shift s
            JOIN FETCH a.staff st
            WHERE st.id = :staffId
              AND s.startTime >= :dayStart
              AND s.startTime < :nextDayStart
            """)
    List<ShiftAssignmentModel> findStaffAssignmentsOnDay(
            @Param("staffId") Long staffId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("nextDayStart") LocalDateTime nextDayStart);

    @Query("""
            SELECT a FROM ShiftAssignmentModel a
            JOIN FETCH a.shift s
            JOIN FETCH a.staff st
            WHERE st.id = :staffId
              AND s.status = :published
              AND s.startTime <= :until
              AND s.endTime >= :since
            ORDER BY s.startTime ASC
            """)
    List<ShiftAssignmentModel> findPublishedAssignmentsOverlapping(
            @Param("staffId") Long staffId,
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until,
            @Param("published") ShiftStatus published);

    @Query("""
            SELECT a FROM ShiftAssignmentModel a
            JOIN FETCH a.shift s
            JOIN FETCH a.staff st
            WHERE st.id = :staffId
              AND s.status = :published
              AND s.startTime >= :dayStart
              AND s.startTime < :nextDayStart
            ORDER BY s.startTime ASC
            """)
    List<ShiftAssignmentModel> findPublishedAssignmentsForStaffBetween(
            @Param("staffId") Long staffId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("nextDayStart") LocalDateTime nextDayStart,
            @Param("published") ShiftStatus published);

    @Query("""
            SELECT a FROM ShiftAssignmentModel a
            JOIN FETCH a.shift s
            JOIN FETCH a.staff st
            WHERE st.id = :staffId
              AND s.status = :published
              AND s.endTime >= :from
            ORDER BY s.startTime ASC
            """)
    List<ShiftAssignmentModel> findPublishedAssignmentsFrom(
            @Param("staffId") Long staffId,
            @Param("from") LocalDateTime from,
            @Param("published") ShiftStatus published);
}
