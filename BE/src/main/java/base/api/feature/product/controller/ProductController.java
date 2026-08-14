package base.api.feature.product.controller;

import base.api.feature.product.dto.request.CreateProductRequest;
import base.api.feature.product.dto.request.UpdateProductRequest;
import base.api.feature.product.dto.response.ProductResponse;
import base.api.feature.product.dto.request.ScheduleProductSalePriceRequest;
import base.api.feature.product.dto.response.ProductSalePriceResponse;
import base.api.feature.product.service.IProductService;
import base.api.feature.product.service.ProductSalePriceService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.dto.PageResponseDTO;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Backend CRUD cho product management")
public class ProductController extends BaseAPIController {

    @Autowired
    private IProductService productService;

    @Autowired
    private ProductSalePriceService productSalePriceService;

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

    @Operation(summary = "Get products (soft-capped; prefer /page)")
    @GetMapping
    public ResponseEntity<TFUResponse<List<ProductResponse>>> getAll() {
        return success(productService.getAll());
    }

    @Operation(summary = "Count visible products for the current user")
    @GetMapping("/count")
    public ResponseEntity<TFUResponse<Long>> count() {
        return success(productService.countVisible());
    }

    @Operation(summary = "Search, filter and paginate products")
    @GetMapping("/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<ProductResponse>>> getPage(
            @ModelAttribute PageRequestDTO pageRequest,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String scope,
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            @RequestParam(required = false) String stockSort) {
        return successPage(productService.getPage(
                pageRequest, categoryId, status, scope, lowStockOnly, stockSort));
    }

    @Operation(summary = "Generate unique EAN-13 barcode (893 prefix)")
    @PreAuthorize("@permissionChecker.has('PRODUCT_MANAGEMENT')")
    @PostMapping("/generate-barcode")
    public ResponseEntity<TFUResponse<String>> generateBarcode() {
        return success(productService.generateBarcode(), "Barcode generated.");
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

    @Operation(summary = "View dated retail-price history")
    @PreAuthorize("@permissionChecker.hasAny('SET_RETAIL_PRICE','VIEW_SUPPLIER_RECEIPTS_PRICES')")
    @GetMapping("/{id}/sale-prices")
    public ResponseEntity<TFUResponse<List<ProductSalePriceResponse>>> salePrices(@PathVariable Integer id) {
        return success(productSalePriceService.history(id));
    }

    @Operation(summary = "Schedule a retail price for a future business day")
    @PreAuthorize("@permissionChecker.has('SET_RETAIL_PRICE')")
    @PostMapping("/{id}/sale-prices")
    public ResponseEntity<TFUResponse<ProductSalePriceResponse>> scheduleSalePrice(
            @PathVariable Integer id,
            @Valid @RequestBody ScheduleProductSalePriceRequest request) {
        return success(productSalePriceService.schedule(id, request), "Retail price scheduled.");
    }
}
