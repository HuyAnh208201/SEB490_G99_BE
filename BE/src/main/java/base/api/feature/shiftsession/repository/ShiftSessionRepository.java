package base.api.feature.shiftsession.repository;

import base.api.shared.entity.ShiftSessionModel;
import base.api.shared.enums.ShiftSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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

    List<ShiftSessionModel> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    Optional<ShiftSessionModel> findFirstByBranchIdAndStatusAndRoleOrderByClosedAtDesc(
            Long branchId,
            ShiftSessionStatus status,
            base.api.shared.enums.UserRole role);
}
