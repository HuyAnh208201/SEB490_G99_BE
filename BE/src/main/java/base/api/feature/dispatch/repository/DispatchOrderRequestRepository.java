package base.api.feature.dispatch.repository;

import base.api.shared.entity.DispatchOrderRequestModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DispatchOrderRequestRepository extends JpaRepository<DispatchOrderRequestModel, Long> {

    List<DispatchOrderRequestModel> findByDispatchOrderId(Long dispatchOrderId);

    List<DispatchOrderRequestModel> findByDispatchOrderIdIn(Collection<Long> dispatchOrderIds);

    List<DispatchOrderRequestModel> findByPurchaseRequestIdIn(Collection<Long> purchaseRequestIds);

    java.util.Optional<DispatchOrderRequestModel> findFirstByPurchaseRequestId(Long purchaseRequestId);
}
