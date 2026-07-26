package base.api.feature.posorder.repository;

import base.api.shared.entity.OrderDiscountModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderDiscountRepository extends JpaRepository<OrderDiscountModel, Long> {
}
