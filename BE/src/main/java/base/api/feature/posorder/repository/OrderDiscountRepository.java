package base.api.feature.posorder.repository;

import base.api.shared.entity.OrderDiscountModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderDiscountRepository extends JpaRepository<OrderDiscountModel, Long> {

    List<OrderDiscountModel> findByOrderId(Long orderId);

    /** A code once applied to an invoice cannot be deleted, even after it returns to active. */
    boolean existsByVoucherId(Long voucherId);
}
