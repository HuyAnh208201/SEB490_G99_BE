package base.api.feature.dispatch.repository;

import base.api.shared.entity.DispatchOrderModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DispatchOrderRepository extends JpaRepository<DispatchOrderModel, Long> {

    List<DispatchOrderModel> findAllByOrderByCreatedAtDesc();
}
