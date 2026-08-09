package base.api.feature.category.repository;

import base.api.shared.entity.CategoryModel;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ICategoryRepository extends JpaRepository<CategoryModel, Integer>, JpaSpecificationExecutor<CategoryModel> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    boolean existsByParentCategoryId(Integer parentId);

    List<CategoryModel> findAll(Sort sort);

    List<CategoryModel> findByActiveTrue(Sort sort);

    List<CategoryModel> findByShortDateTrue();
}
