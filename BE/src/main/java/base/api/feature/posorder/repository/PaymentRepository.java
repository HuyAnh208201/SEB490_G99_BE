package base.api.feature.posorder.repository;

import base.api.shared.entity.PaymentModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentModel, Long> {

    List<PaymentModel> findByOrderIdIn(Collection<Long> orderIds);
}
