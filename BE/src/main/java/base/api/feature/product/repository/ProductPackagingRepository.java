package base.api.feature.product.repository;

import base.api.shared.entity.ProductPackagingModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductPackagingRepository extends JpaRepository<ProductPackagingModel, Integer> {

    List<ProductPackagingModel> findByProductIdOrderBySortOrderAscIdAsc(Integer productId);

    List<ProductPackagingModel> findByProductIdInOrderByProductIdAscSortOrderAsc(Collection<Integer> productIds);

    Optional<ProductPackagingModel> findFirstByProductIdAndIsPurchaseDefaultTrue(Integer productId);

    List<ProductPackagingModel> findByProductIdInAndIsPurchaseDefaultTrue(Collection<Integer> productIds);

    boolean existsByProductId(Integer productId);
}
