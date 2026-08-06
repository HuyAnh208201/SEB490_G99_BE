package base.api.feature.product.controller;

import base.api.feature.product.dto.response.PosCatalogItemResponse;
import base.api.feature.product.service.IProductService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.dto.PageResponseDTO;
import base.api.shared.dto.TFUResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pos")
@Tag(name = "POS Catalog", description = "Lightweight catalog for the cashier counter")
public class PosCatalogController extends BaseAPIController {

    @Autowired
    private IProductService productService;

    @Operation(summary = "Active products for POS (soft-capped; prefer /catalog/page)")
    @PreAuthorize("@permissionChecker.has('POS_CHECKOUT')")
    @GetMapping("/catalog")
    public ResponseEntity<TFUResponse<List<PosCatalogItemResponse>>> getCatalog() {
        return success(productService.getPosCatalog());
    }

    @Operation(summary = "Paged POS catalog with optional search / category")
    @PreAuthorize("@permissionChecker.has('POS_CHECKOUT')")
    @GetMapping("/catalog/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<PosCatalogItemResponse>>> getCatalogPage(
            @ModelAttribute PageRequestDTO pageRequest,
            @RequestParam(required = false) Integer categoryId) {
        return successPage(productService.getPosCatalogPage(pageRequest, categoryId));
    }
}
