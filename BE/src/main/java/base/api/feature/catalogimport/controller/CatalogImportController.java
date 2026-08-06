package base.api.feature.catalogimport.controller;

import base.api.feature.catalogimport.dto.CatalogImportStatusResponse;
import base.api.feature.catalogimport.service.CatalogImportService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin one-shot VN catalog import — slow background job, not startup seed.
 */
@RestController
@RequestMapping("/api/admin/catalog-import")
@Tag(name = "Catalog Import", description = "One-shot slow import of extra VN market SKUs")
public class CatalogImportController extends BaseAPIController {

    @Autowired
    private CatalogImportService catalogImportService;

    @Operation(summary = "Start slow catalog import (Admin only; runs once in background)")
    @PreAuthorize("@permissionChecker.has('PRODUCT_MANAGEMENT')")
    @PostMapping("/start")
    public ResponseEntity<TFUResponse<CatalogImportStatusResponse>> start() {
        return success(catalogImportService.start(), "Catalog import started.");
    }

    @Operation(summary = "Catalog import job status")
    @PreAuthorize("@permissionChecker.has('PRODUCT_MANAGEMENT')")
    @GetMapping("/status")
    public ResponseEntity<TFUResponse<CatalogImportStatusResponse>> status() {
        return success(catalogImportService.status());
    }
}
