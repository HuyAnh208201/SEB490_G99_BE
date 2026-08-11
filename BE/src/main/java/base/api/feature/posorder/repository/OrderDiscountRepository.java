package base.api.feature.posorder.repository;

import base.api.shared.entity.OrderDiscountModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderDiscountRepository extends JpaRepository<OrderDiscountModel, Long> {

    List<OrderDiscountModel> findByOrderId(Long orderId);

    /** Mã đã từng áp vào hoá đơn thì không được xoá, kể cả khi đã nhả về active. */
    boolean existsByVoucherId(Long voucherId);
}
