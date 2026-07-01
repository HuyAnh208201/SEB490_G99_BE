package base.api.feature.product.controller;

import base.api.feature.product.dto.request.CreateProductRequest;
import base.api.feature.product.dto.request.UpdateProductRequest;
import base.api.feature.product.dto.response.ProductResponse;
import base.api.feature.product.service.IProductService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Backend CRUD cho product management")
public class ProductController extends BaseAPIController {

    @Autowired
    private IProductService productService;

    @Operation(summary = "Create product")
    @PreAuthorize("@permissionChecker.has('PRODUCT_MANAGEMENT')")
    @PostMapping
    public ResponseEntity<TFUResponse<ProductResponse>> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse data = productService.create(request);
        TFUResponse<ProductResponse> body = new TFUResponse<>(
                true, data, "Product created successfully.", HttpStatus.CREATED.value(), null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Get all products")
    @GetMapping
    public ResponseEntity<TFUResponse<List<ProductResponse>>> getAll() {
        return success(productService.getAll());
    }

    @Operation(summary = "Get product detail")
    @GetMapping("/{id}")
    public ResponseEntity<TFUResponse<ProductResponse>> getById(@PathVariable Integer id) {
        return success(productService.getById(id));
    }

    @Operation(summary = "Update product")
    @PreAuthorize("@permissionChecker.has('PRODUCT_MANAGEMENT')")
    @PutMapping("/{id}")
    public ResponseEntity<TFUResponse<ProductResponse>> update(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateProductRequest request) {
        return success(productService.update(id, request), "Product updated successfully.");
    }

    @Operation(summary = "Delete product")
    @PreAuthorize("@permissionChecker.has('PRODUCT_MANAGEMENT')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
