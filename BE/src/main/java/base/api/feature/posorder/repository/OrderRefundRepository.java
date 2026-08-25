package base.api.feature.posorder.repository;

import base.api.shared.entity.OrderRefundModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderRefundRepository extends JpaRepository<OrderRefundModel, Long> {

    List<OrderRefundModel> findByBranchIdAndStatusOrderByCreatedAtDesc(Long branchId, String status);

    List<OrderRefundModel> findByBranchIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            Long branchId,
            String status,
            java.time.LocalDateTime from,
            java.time.LocalDateTime to);

    /** Chặn xin refund trùng khi đơn đã có yêu cầu PENDING hoặc đã APPROVED. */
    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<String> statuses);

    long countByBranchIdAndStatus(Long branchId, String status);
}
