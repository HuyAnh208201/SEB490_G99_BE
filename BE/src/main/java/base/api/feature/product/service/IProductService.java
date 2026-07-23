package base.api.feature.product.service;

import base.api.feature.product.dto.request.CreateProductRequest;
import base.api.feature.product.dto.request.UpdateProductRequest;
import base.api.feature.product.dto.response.ProductResponse;

import java.util.List;

public interface IProductService {

    ProductResponse create(CreateProductRequest request);

    ProductResponse update(Integer id, UpdateProductRequest request);

    void delete(Integer id);

    ProductResponse getById(Integer id);

    List<ProductResponse> getAll();

    ProductResponse scanByBarcode(String barcode);

    String generateBarcode();
}
