package base.api.feature.purchaserequest.repository;

import base.api.shared.entity.GoodsReceiptModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GoodsReceiptRepository extends JpaRepository<GoodsReceiptModel, Long> {

    List<GoodsReceiptModel> findByPurchaseRequestId(Long purchaseRequestId);
}
