package base.api.feature.promotion.repository;

import base.api.shared.entity.CampaignModel;
import base.api.shared.enums.CampaignScope;
import base.api.shared.enums.CampaignStatus;
import base.api.shared.enums.CampaignType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface CampaignRepository extends JpaRepository<CampaignModel, Long>, JpaSpecificationExecutor<CampaignModel> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<CampaignModel> findByScopeOrderByIdAsc(CampaignScope scope);

    List<CampaignModel> findByIdIn(Collection<Long> ids);

    List<CampaignModel> findByStatus(CampaignStatus status);

    List<CampaignModel> findByType(CampaignType type);

    long countByStatus(CampaignStatus status);

    /** ACTIVE campaigns currently within [startAt, endAt] — same window as customer mobile API. */
    @Query("""
            SELECT COUNT(c) FROM CampaignModel c
            WHERE c.status = :status
              AND c.startAt <= :now
              AND c.endAt >= :now
            """)
    long countLiveByStatus(@Param("status") CampaignStatus status, @Param("now") LocalDateTime now);

    @Query("""
            SELECT c FROM CampaignModel c
            WHERE c.status = :status
              AND c.startAt <= :now
              AND c.endAt >= :now
            ORDER BY c.priority DESC, c.endAt ASC
            """)
    List<CampaignModel> findLiveByStatus(
            @Param("status") CampaignStatus status,
            @Param("now") LocalDateTime now);
}
