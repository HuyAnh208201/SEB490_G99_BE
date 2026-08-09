package base.api.feature.dispatch.repository;

import base.api.shared.entity.DispatchOrderSupplierModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DispatchOrderSupplierRepository extends JpaRepository<DispatchOrderSupplierModel, Long> {

    List<DispatchOrderSupplierModel> findByDispatchOrderId(Long dispatchOrderId);

    List<DispatchOrderSupplierModel> findByDispatchOrderIdIn(Collection<Long> dispatchOrderIds);

    void deleteByDispatchOrderId(Long dispatchOrderId);
}
