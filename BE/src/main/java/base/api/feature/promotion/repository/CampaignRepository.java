package base.api.feature.promotion.repository;

import base.api.shared.entity.CampaignModel;
import base.api.shared.enums.CampaignScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CampaignRepository extends JpaRepository<CampaignModel, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<CampaignModel> findByScopeOrderByIdAsc(CampaignScope scope);

    List<CampaignModel> findByIdIn(Collection<Long> ids);

    List<CampaignModel> findByStatus(base.api.shared.enums.CampaignStatus status);
}
