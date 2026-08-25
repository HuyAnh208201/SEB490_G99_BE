package base.api.feature.purchaserequest.repository;

import base.api.shared.entity.BranchInventoryModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BranchInventoryRepository extends JpaRepository<BranchInventoryModel, Long>, JpaSpecificationExecutor<BranchInventoryModel> {

    List<BranchInventoryModel> findByBranchId(Long branchId);

    boolean existsByProductId(Integer productId);

    Optional<BranchInventoryModel> findByBranchIdAndProductId(Long branchId, Integer productId);

    List<BranchInventoryModel> findByBranchIdAndProductIdIn(Long branchId, Collection<Integer> productIds);

    /**
     * Trừ tồn kho atomic khi bán hàng — chỉ thành công nếu còn đủ hàng.
     * Khớp 0 row nghĩa là hết hàng hoặc quầy khác vừa bán mất, khi đó cả đơn
     * phải rollback chứ không được để tồn kho âm.
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE BranchInventoryModel b SET b.currentStock = b.currentStock - :quantity
            WHERE b.branchId = :branchId AND b.productId = :productId AND b.currentStock >= :quantity
            """)
    int deductStock(
            @Param("branchId") Long branchId,
            @Param("productId") Integer productId,
            @Param("quantity") Integer quantity);

    /**
     * Cộng lại tồn kho khi hoàn/trả hàng — không có điều kiện chặn như deductStock
     * vì trả hàng luôn hợp lệ, chỉ đơn thuần đưa số lượng đã bán về lại kho.
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE BranchInventoryModel b SET b.currentStock = b.currentStock + :quantity
            WHERE b.branchId = :branchId AND b.productId = :productId
            """)
    int addStock(
            @Param("branchId") Long branchId,
            @Param("productId") Integer productId,
            @Param("quantity") Integer quantity);
}
