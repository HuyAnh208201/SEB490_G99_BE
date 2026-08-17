package base.api.feature.posorder.repository;

import base.api.shared.entity.OrderModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<OrderModel, Long>, JpaSpecificationExecutor<OrderModel> {

    List<OrderModel> findByBranchIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            Long branchId,
            LocalDateTime from,
            LocalDateTime to);

    List<OrderModel> findByBranchIdAndShiftIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            Long branchId,
            Long shiftId,
            LocalDateTime from,
            LocalDateTime to);

    List<OrderModel> findTop50ByBranchIdOrderByCreatedAtDesc(Long branchId);

    List<OrderModel> findTop50ByBranchIdAndShiftIdOrderByCreatedAtDesc(Long branchId, Long shiftId);

    long countByBranchId(Long branchId);

    boolean existsByCashierId(Long cashierId);

    Page<OrderModel> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);
}
