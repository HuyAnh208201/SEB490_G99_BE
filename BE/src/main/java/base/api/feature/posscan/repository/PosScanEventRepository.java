package base.api.feature.posscan.repository;

import base.api.shared.entity.PosScanEventModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PosScanEventRepository extends JpaRepository<PosScanEventModel, Long> {

    /**
     * Các mã quét mới hơn con trỏ của chính thu ngân đó.
     * Chặn thêm theo thời gian để máy bán hàng mở muộn không nuốt lại mã cũ từ hôm trước.
     */
    List<PosScanEventModel> findByCashierUserIdAndIdGreaterThanAndCreatedAtAfterOrderByIdAsc(
            Long cashierUserId, Long id, LocalDateTime createdAfter);

    @Query("SELECT COALESCE(MAX(e.id), 0) FROM PosScanEventModel e WHERE e.cashierUserId = :cashierUserId")
    Long findLatestIdByCashierUserId(@Param("cashierUserId") Long cashierUserId);

    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
