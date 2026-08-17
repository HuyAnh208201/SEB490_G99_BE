package base.api.feature.system.repository;

import base.api.shared.entity.MembershipTierModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipTierRepository extends JpaRepository<MembershipTierModel, Long> {

    List<MembershipTierModel> findAllByOrderBySortOrderAsc();

    List<MembershipTierModel> findByActiveTrueOrderBySortOrderAsc();

    Optional<MembershipTierModel> findByCode(String code);
}
