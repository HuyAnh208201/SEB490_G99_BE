package base.api.feature.product.repository;

import base.api.shared.entity.ProductModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface IProductRepository extends JpaRepository<ProductModel, Integer>, JpaSpecificationExecutor<ProductModel> {

    @Override
    @EntityGraph(attributePaths = "category")
    Page<ProductModel> findAll(Specification<ProductModel> specification, Pageable pageable);

    boolean existsByCode(String code);

    boolean existsByBarcode(String barcode);

    boolean existsByBarcodeAndIdNot(String barcode, Integer id);

    @EntityGraph(attributePaths = "category")
    Optional<ProductModel> findByBarcode(String barcode);

    boolean existsByCategory_Id(Integer categoryId);

    @EntityGraph(attributePaths = "category")
    @Query("SELECT p FROM ProductModel p WHERE p.id = :id")
    Optional<ProductModel> findByIdWithCategory(@Param("id") Integer id);

    @EntityGraph(attributePaths = "category")
    @Query("""
            SELECT p FROM ProductModel p
            WHERE LOWER(p.status) = 'active'
            AND (:keyword IS NULL OR :keyword = ''
                OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<ProductModel> searchActiveProducts(@Param("keyword") String keyword, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    @Query("SELECT p FROM ProductModel p WHERE LOWER(p.status) = 'active'")
    List<ProductModel> findAllActiveProducts();

    @EntityGraph(attributePaths = "category")
    @Query("SELECT p FROM ProductModel p WHERE p.id IN :ids")
    List<ProductModel> findByIdInWithCategory(@Param("ids") Collection<Integer> ids);

    @Query("SELECT p.barcode FROM ProductModel p WHERE p.barcode IS NOT NULL")
    List<String> findAllBarcodes();

    @EntityGraph(attributePaths = "category")
    @Query("""
            SELECT p FROM ProductModel p
            WHERE :supervisor = true
               OR p.scope = 'GLOBAL'
               OR (:branchId IS NOT NULL AND p.branchId = :branchId)
            ORDER BY p.id ASC
            """)
    List<ProductModel> findVisibleProducts(
            @Param("supervisor") boolean supervisor,
            @Param("branchId") Long branchId);

    @EntityGraph(attributePaths = "category")
    @Query("""
            SELECT p FROM ProductModel p
            WHERE LOWER(p.status) = 'active'
              AND (
                    :supervisor = true
                 OR p.scope = 'GLOBAL'
                 OR (:branchId IS NOT NULL AND p.branchId = :branchId)
              )
            ORDER BY p.code ASC
            """)
    List<ProductModel> findVisibleActiveProducts(
            @Param("supervisor") boolean supervisor,
            @Param("branchId") Long branchId);

    long countByScopeAndBranchId(String scope, Long branchId);
}
