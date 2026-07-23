package base.api.feature.barcode.controller;

import base.api.feature.barcode.dto.request.BarcodeScanRequest;
import base.api.feature.product.dto.response.ProductResponse;
import base.api.feature.product.service.IProductService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/barcode")
@Tag(name = "Barcode", description = "Barcode scanning for cashier POS")
public class BarcodeController extends BaseAPIController {

    private final IProductService productService;

    public BarcodeController(IProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Scan a product barcode for the current cashier branch")
    @PreAuthorize("@permissionChecker.has('CASHIER_ADD_POINTS')")
    @PostMapping("/scan")
    public ResponseEntity<TFUResponse<ProductResponse>> scan(@Valid @RequestBody BarcodeScanRequest request) {
        return success(productService.scanByBarcode(request.getBarcode()));
    }
}
