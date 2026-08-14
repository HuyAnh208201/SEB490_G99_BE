package base.api.feature.purchaserequest.repository;

import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.enums.PurchaseRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequestModel, Long>, JpaSpecificationExecutor<PurchaseRequestModel> {

    Page<PurchaseRequestModel> findByBranchId(Long branchId, Pageable pageable);

    Page<PurchaseRequestModel> findByStatusIn(Collection<PurchaseRequestStatus> statuses, Pageable pageable);

    List<PurchaseRequestModel> findByStatus(PurchaseRequestStatus status);

    List<PurchaseRequestModel> findByBranchIdAndStatusIn(Long branchId, Collection<PurchaseRequestStatus> statuses);

    Optional<PurchaseRequestModel> findFirstBySupplementalForReceiptIdOrderByIdDesc(Long receiptId);

    long countByStatus(PurchaseRequestStatus status);

    long countByBranchIdAndStatusIn(Long branchId, Collection<PurchaseRequestStatus> statuses);

    long countByStatusIn(Collection<PurchaseRequestStatus> statuses);

    @Query("""
            SELECT DISTINCT pr.branchId FROM PurchaseRequestModel pr
            WHERE pr.branchId IS NOT NULL AND pr.status IN :statuses
            """)
    List<Long> findDistinctBranchIdsByStatusIn(@Param("statuses") Collection<PurchaseRequestStatus> statuses);
}
