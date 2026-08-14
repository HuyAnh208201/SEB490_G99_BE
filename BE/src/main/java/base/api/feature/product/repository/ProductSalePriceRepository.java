package base.api.feature.product.repository;

import base.api.shared.entity.ProductSalePriceModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductSalePriceRepository extends JpaRepository<ProductSalePriceModel, Long> {
    Optional<ProductSalePriceModel> findFirstByProductIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
            Integer productId, LocalDate date);

    Optional<ProductSalePriceModel> findFirstByProductIdAndEffectiveDateGreaterThanOrderByEffectiveDateAsc(
            Integer productId, LocalDate date);

    List<ProductSalePriceModel> findByProductIdOrderByEffectiveDateDesc(Integer productId);

    boolean existsByProductIdAndEffectiveDate(Integer productId, LocalDate effectiveDate);

    @Query("""
            SELECT price
            FROM ProductSalePriceModel price
            WHERE price.productId IN :productIds
              AND price.effectiveDate = (
                  SELECT MAX(candidate.effectiveDate)
                  FROM ProductSalePriceModel candidate
                  WHERE candidate.productId = price.productId
                    AND candidate.effectiveDate <= :date
              )
            """)
    List<ProductSalePriceModel> findEffectivePrices(
            @Param("productIds") List<Integer> productIds,
            @Param("date") LocalDate date);
}
