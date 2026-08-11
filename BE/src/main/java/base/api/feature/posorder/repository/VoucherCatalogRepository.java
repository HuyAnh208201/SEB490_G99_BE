package base.api.feature.posorder.repository;

import base.api.shared.entity.VoucherCatalogModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VoucherCatalogRepository extends JpaRepository<VoucherCatalogModel, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<VoucherCatalogModel> findAllByOrderByIdAsc();

    Page<VoucherCatalogModel> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
