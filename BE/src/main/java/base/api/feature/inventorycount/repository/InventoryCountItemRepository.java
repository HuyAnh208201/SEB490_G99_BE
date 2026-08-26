package base.api.feature.inventorycount.repository;

import base.api.shared.entity.InventoryCountItemModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

@Repository
public interface InventoryCountItemRepository extends JpaRepository<InventoryCountItemModel, Long> {

    List<InventoryCountItemModel> findBySessionId(Long sessionId);

    @Query("""
            SELECT i.sessionId, COUNT(i)
            FROM InventoryCountItemModel i
            WHERE i.sessionId IN :sessionIds AND i.variance <> 0
            GROUP BY i.sessionId
            """)
    List<Object[]> countVarianceBySessionIds(@Param("sessionIds") Collection<Long> sessionIds);
}
