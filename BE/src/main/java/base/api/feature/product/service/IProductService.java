package base.api.feature.product.service;

import base.api.feature.product.dto.request.CreateProductRequest;
import base.api.feature.product.dto.request.UpdateProductRequest;
import base.api.feature.product.dto.response.ProductResponse;
import base.api.shared.dto.PageRequestDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface IProductService {

    ProductResponse create(CreateProductRequest request);

    ProductResponse update(Integer id, UpdateProductRequest request);

    void delete(Integer id);

    ProductResponse getById(Integer id);

    List<ProductResponse> getAll();

    Page<ProductResponse> getPage(
            PageRequestDTO pageRequest,
            Integer categoryId,
            String status,
            String scope,
            boolean lowStockOnly);

    ProductResponse scanByBarcode(String barcode);

    String generateBarcode();
}
