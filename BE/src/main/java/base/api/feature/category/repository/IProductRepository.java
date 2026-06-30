package base.api.feature.category.repository;

import base.api.shared.entity.ProductModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IProductRepository extends JpaRepository<ProductModel, Integer> {

    boolean existsByCategoryId(Integer categoryId);
}
