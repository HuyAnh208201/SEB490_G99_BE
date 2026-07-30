package base.api.feature.report.repository;

import base.api.feature.report.dto.CashDiscrepancyRow;
import base.api.shared.entity.ShiftSessionModel;
import base.api.shared.enums.ShiftSessionStatus;
import base.api.shared.enums.UserRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Truy vấn chênh lệch tiền ca phục vụ báo cáo trên bảng shift_sessions.
 *
 * Chỉ lấy ca thu ngân (role CASHIER) đã chốt sau BM duyệt chênh lệch (status COMPLETED hoặc APPROVED legacy);
 * lọc theo chi nhánh (optional) và thời điểm chốt ca closedAt trong [from, to).
 */
@Repository
public interface ReportShiftSessionRepository extends JpaRepository<ShiftSessionModel, Long> {

    @Query("""
            SELECT new base.api.feature.report.dto.CashDiscrepancyRow(
                s.id, s.shiftId, s.employeeId, s.expectedCash, s.actualCash,
                s.difference, s.approvedBy, s.managerNote, s.closedAt)
            FROM ShiftSessionModel s
            WHERE s.role = :role
              AND s.status IN :statuses
              AND (:branchId IS NULL OR s.branchId = :branchId)
              AND (:from IS NULL OR s.closedAt >= :from)
              AND (:to IS NULL OR s.closedAt < :to)
            ORDER BY s.closedAt DESC
            """)
    List<CashDiscrepancyRow> findDiscrepancies(
            @Param("role") UserRole role,
            @Param("statuses") List<ShiftSessionStatus> statuses,
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query(value = """
            SELECT new base.api.feature.report.dto.CashDiscrepancyRow(
                s.id, s.shiftId, s.employeeId, s.expectedCash, s.actualCash,
                s.difference, s.approvedBy, s.managerNote, s.closedAt)
            FROM ShiftSessionModel s
            WHERE s.role = :role
              AND s.status IN :statuses
              AND (:branchId IS NULL OR s.branchId = :branchId)
              AND (:from IS NULL OR s.closedAt >= :from)
              AND (:to IS NULL OR s.closedAt < :to)
              AND (:search IS NULL OR LOWER(COALESCE(s.managerNote, '')) LIKE :search)
            ORDER BY s.closedAt DESC
            """, countQuery = """
            SELECT COUNT(s)
            FROM ShiftSessionModel s
            WHERE s.role = :role
              AND s.status IN :statuses
              AND (:branchId IS NULL OR s.branchId = :branchId)
              AND (:from IS NULL OR s.closedAt >= :from)
              AND (:to IS NULL OR s.closedAt < :to)
              AND (:search IS NULL OR LOWER(COALESCE(s.managerNote, '')) LIKE :search)
            """)
    Page<CashDiscrepancyRow> findDiscrepancyPage(
            @Param("role") UserRole role,
            @Param("statuses") List<ShiftSessionStatus> statuses,
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("search") String search,
            Pageable pageable);
}
