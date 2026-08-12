package base.api.feature.posorder.repository;

import base.api.shared.entity.VoucherModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<VoucherModel, Long>,
        JpaSpecificationExecutor<VoucherModel> {

    Optional<VoucherModel> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    /** Used to refuse deleting a voucher type while issued codes still reference it. */
    boolean existsByVoucherCatalogId(Long voucherCatalogId);

    /**
     * Đánh dấu đã dùng theo kiểu atomic. Khớp 0 row nghĩa là quầy khác vừa dùng
     * mã này trước, khi đó cả đơn phải rollback thay vì giảm giá hai lần.
     */
    @Modifying
    @Transactional
    @Query("UPDATE VoucherModel v SET v.status = 'used' WHERE v.id = :id AND v.status = 'active'")
    int markUsed(@Param("id") Long id);

    /**
     * Returns a code to usable when a PAYOS order is cancelled or expires. The status =
     * 'used' guard stops a second cancel from releasing a code spent on another order.
     */
    @Modifying
    @Transactional
    @Query("UPDATE VoucherModel v SET v.status = 'active' WHERE v.id = :id AND v.status = 'used'")
    int markActive(@Param("id") Long id);
}
