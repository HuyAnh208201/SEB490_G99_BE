package base.api.feature.purchaserequest.repository;

import base.api.shared.entity.GoodsReceiptItemModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GoodsReceiptItemRepository extends JpaRepository<GoodsReceiptItemModel, Long> {

    List<GoodsReceiptItemModel> findByGoodsReceiptId(Long goodsReceiptId);
}
