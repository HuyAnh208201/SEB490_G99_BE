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

    /** Dùng để chặn xoá một loại voucher khi còn mã đã phát tham chiếu tới nó. */
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
     * Nhả mã về lại trạng thái dùng được khi đơn PAYOS bị huỷ/hết hạn. Điều kiện
     * status = 'used' để hai lần huỷ liên tiếp không nhả nhầm mã đã dùng cho đơn khác.
     */
    @Modifying
    @Transactional
    @Query("UPDATE VoucherModel v SET v.status = 'active' WHERE v.id = :id AND v.status = 'used'")
    int markActive(@Param("id") Long id);
}
