package base.api.feature.supplier.repository;

import base.api.shared.entity.SupplierModel;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ISupplierRepository extends JpaRepository<SupplierModel, Integer>, JpaSpecificationExecutor<SupplierModel> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    List<SupplierModel> findAll(Sort sort);
}
