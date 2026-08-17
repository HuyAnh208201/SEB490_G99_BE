package base.api.feature.purchaserequest.repository;

import base.api.shared.entity.GoodsReceiptModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GoodsReceiptRepository extends JpaRepository<GoodsReceiptModel, Long>, JpaSpecificationExecutor<GoodsReceiptModel> {

    List<GoodsReceiptModel> findByPurchaseRequestId(Long purchaseRequestId);

    List<GoodsReceiptModel> findByBranchIdOrderByReceivedAtDesc(Long branchId);

    boolean existsByDispatchOrderIdAndPurchaseRequestIdAndStatus(
            Long dispatchOrderId,
            Long purchaseRequestId,
            String status);

    Optional<GoodsReceiptModel> findByDispatchOrderIdAndPurchaseRequestIdAndStatus(
            Long dispatchOrderId,
            Long purchaseRequestId,
            String status);

    List<GoodsReceiptModel> findByPurchaseRequestIdInAndStatus(
            Collection<Long> purchaseRequestIds,
            String status);

    List<GoodsReceiptModel> findByPurchaseRequestIdIn(Collection<Long> purchaseRequestIds);
}
