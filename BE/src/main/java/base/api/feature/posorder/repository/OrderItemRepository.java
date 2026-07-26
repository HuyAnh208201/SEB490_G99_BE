package base.api.feature.posorder.repository;

import base.api.shared.entity.OrderItemModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItemModel, Long> {

    List<OrderItemModel> findByOrderIdIn(Collection<Long> orderIds);
}
