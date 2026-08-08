package base.api.feature.product.service;

import base.api.feature.product.dto.request.CreateProductRequest;
import base.api.feature.product.dto.request.UpdateProductRequest;
import base.api.feature.product.dto.response.PosCatalogItemResponse;
import base.api.feature.product.dto.response.ProductResponse;
import base.api.shared.dto.PageRequestDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface IProductService {

    ProductResponse create(CreateProductRequest request);

    ProductResponse update(Integer id, UpdateProductRequest request);

    void delete(Integer id);

    ProductResponse getById(Integer id);

    /**
     * Soft-capped list (max {@link PageRequestDTO#MAX_PAGE_SIZE}) for legacy callers.
     * Prefer {@link #getPage} or {@link #countVisible}.
     */
    List<ProductResponse> getAll();

    long countVisible();

    /** Soft-capped POS catalog (legacy). Prefer {@link #getPosCatalogPage}. */
    List<PosCatalogItemResponse> getPosCatalog();

    Page<PosCatalogItemResponse> getPosCatalogPage(PageRequestDTO pageRequest, Integer categoryId);

    Page<ProductResponse> getPage(
            PageRequestDTO pageRequest,
            Integer categoryId,
            String status,
            String scope,
            boolean lowStockOnly,
            String stockSort);

    ProductResponse scanByBarcode(String barcode);

    String generateBarcode();
}
