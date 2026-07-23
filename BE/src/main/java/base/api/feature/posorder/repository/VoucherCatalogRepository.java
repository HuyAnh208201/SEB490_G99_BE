package base.api.feature.posorder.repository;

import base.api.shared.entity.VoucherCatalogModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VoucherCatalogRepository extends JpaRepository<VoucherCatalogModel, Long> {
}
