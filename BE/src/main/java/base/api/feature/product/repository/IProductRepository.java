package base.api.feature.product.repository;

import base.api.shared.entity.ProductModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IProductRepository extends JpaRepository<ProductModel, Integer> {

    boolean existsByCode(String code);

    boolean existsByBarcode(String barcode);

    boolean existsByBarcodeAndIdNot(String barcode, Integer id);

    boolean existsByCategory_Id(Integer categoryId);
}
