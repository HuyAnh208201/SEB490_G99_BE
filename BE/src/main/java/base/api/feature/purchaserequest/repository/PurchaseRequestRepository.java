package base.api.feature.purchaserequest.repository;

import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.enums.PurchaseRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequestModel, Long> {

    Page<PurchaseRequestModel> findByBranchId(Long branchId, Pageable pageable);

    Page<PurchaseRequestModel> findByStatusIn(Collection<PurchaseRequestStatus> statuses, Pageable pageable);

    List<PurchaseRequestModel> findByStatus(PurchaseRequestStatus status);
}
