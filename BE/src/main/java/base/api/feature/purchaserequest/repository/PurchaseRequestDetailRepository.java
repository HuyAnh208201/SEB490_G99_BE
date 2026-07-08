package base.api.feature.purchaserequest.repository;

import base.api.shared.entity.PurchaseRequestDetailModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseRequestDetailRepository extends JpaRepository<PurchaseRequestDetailModel, Long> {

    List<PurchaseRequestDetailModel> findByPurchaseRequestIdOrderByIdAsc(Long purchaseRequestId);

    List<PurchaseRequestDetailModel> findByPurchaseRequestIdIn(Collection<Long> purchaseRequestIds);

    Optional<PurchaseRequestDetailModel> findByPurchaseRequestIdAndProductId(Long purchaseRequestId, Integer productId);

    void deleteByPurchaseRequestId(Long purchaseRequestId);

    long countByPurchaseRequestId(Long purchaseRequestId);
}
