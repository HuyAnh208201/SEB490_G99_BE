package base.api.feature.posorder.repository;

import base.api.shared.entity.OrderModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<OrderModel, Long>, JpaSpecificationExecutor<OrderModel> {

    List<OrderModel> findByBranchIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            Long branchId,
            LocalDateTime from,
            LocalDateTime to);

    List<OrderModel> findTop50ByBranchIdOrderByCreatedAtDesc(Long branchId);

    long countByBranchId(Long branchId);
}
