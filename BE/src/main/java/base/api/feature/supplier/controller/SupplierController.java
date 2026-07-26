package base.api.feature.supplier.controller;

import base.api.feature.supplier.dto.request.CreateSupplierRequest;
import base.api.feature.supplier.dto.request.UpdateSupplierRequest;
import base.api.feature.supplier.dto.response.SupplierResponse;
import base.api.feature.supplier.service.ISupplierService;
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
@RequestMapping("/api/suppliers")
@Tag(name = "Suppliers", description = "Backend CRUD cho supplier management")
public class SupplierController extends BaseAPIController {

    @Autowired
    private ISupplierService supplierService;

    @Operation(summary = "Create supplier")
    @PreAuthorize("@permissionChecker.has('SUPPLIER_MANAGEMENT')")
    @PostMapping
    public ResponseEntity<TFUResponse<SupplierResponse>> create(@Valid @RequestBody CreateSupplierRequest request) {
        SupplierResponse data = supplierService.create(request);
        TFUResponse<SupplierResponse> body = new TFUResponse<>(
                true, data, "Supplier created successfully.", HttpStatus.CREATED.value(), null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Get all suppliers")
    @PreAuthorize("@permissionChecker.hasAny('SUPPLIER_MANAGEMENT', 'CHOOSE_EXTERNAL_SUPPLIER')")
    @GetMapping
    public ResponseEntity<TFUResponse<List<SupplierResponse>>> getAll() {
        return success(supplierService.getAll());
    }

    @Operation(summary = "Search, filter and paginate suppliers")
    @PreAuthorize("@permissionChecker.hasAny('SUPPLIER_MANAGEMENT', 'CHOOSE_EXTERNAL_SUPPLIER')")
    @GetMapping("/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<SupplierResponse>>> getPage(
            @ModelAttribute PageRequestDTO pageRequest,
            @RequestParam(required = false) String status) {
        return successPage(supplierService.getPage(pageRequest, status));
    }

    @Operation(summary = "Get supplier detail")
    @PreAuthorize("@permissionChecker.hasAny('SUPPLIER_MANAGEMENT', 'CHOOSE_EXTERNAL_SUPPLIER')")
    @GetMapping("/{id}")
    public ResponseEntity<TFUResponse<SupplierResponse>> getById(@PathVariable Integer id) {
        return success(supplierService.getById(id));
    }

    @Operation(summary = "Update supplier")
    @PreAuthorize("@permissionChecker.has('SUPPLIER_MANAGEMENT')")
    @PutMapping("/{id}")
    public ResponseEntity<TFUResponse<SupplierResponse>> update(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateSupplierRequest request) {
        return success(supplierService.update(id, request), "Supplier updated successfully.");
    }

    @Operation(summary = "Delete supplier")
    @PreAuthorize("@permissionChecker.has('SUPPLIER_MANAGEMENT')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        supplierService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
