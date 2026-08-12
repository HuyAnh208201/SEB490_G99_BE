package base.api.feature.voucher.controller;

import base.api.feature.voucher.dto.request.IssueVoucherRequest;
import base.api.feature.voucher.dto.request.SaveVoucherCatalogRequest;
import base.api.feature.voucher.dto.response.VoucherAdminResponse;
import base.api.feature.voucher.dto.response.VoucherCatalogResponse;
import base.api.feature.voucher.service.IVoucherAdminService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.dto.PageResponseDTO;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Discount Codes", description = "Quản lý loại voucher và các mã đã phát")
public class VoucherAdminController extends BaseAPIController {

    @Autowired
    private IVoucherAdminService voucherAdminService;

    // =========================================================================
    // Voucher types
    // =========================================================================

    @Operation(summary = "Get all discount types")
    @PreAuthorize("@permissionChecker.hasAny('VOUCHER_LIST', 'VOUCHER_MANAGEMENT')")
    @GetMapping("/voucher-catalog")
    public ResponseEntity<TFUResponse<List<VoucherCatalogResponse>>> getAllCatalogs() {
        return success(voucherAdminService.getAllCatalogs());
    }

    @Operation(summary = "Search and paginate discount types")
    @PreAuthorize("@permissionChecker.hasAny('VOUCHER_LIST', 'VOUCHER_MANAGEMENT')")
    @GetMapping("/voucher-catalog/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<VoucherCatalogResponse>>> getCatalogPage(
            @ModelAttribute PageRequestDTO pageRequest) {
        return successPage(voucherAdminService.getCatalogPage(pageRequest));
    }

    @Operation(summary = "Get discount type detail")
    @PreAuthorize("@permissionChecker.hasAny('VOUCHER_LIST', 'VOUCHER_MANAGEMENT')")
    @GetMapping("/voucher-catalog/{id}")
    public ResponseEntity<TFUResponse<VoucherCatalogResponse>> getCatalog(@PathVariable Long id) {
        return success(voucherAdminService.getCatalog(id));
    }

    @Operation(summary = "Create discount type")
    @PreAuthorize("@permissionChecker.has('VOUCHER_MANAGEMENT')")
    @PostMapping("/voucher-catalog")
    public ResponseEntity<TFUResponse<VoucherCatalogResponse>> createCatalog(
            @Valid @RequestBody SaveVoucherCatalogRequest request) {
        VoucherCatalogResponse data = voucherAdminService.createCatalog(request);
        TFUResponse<VoucherCatalogResponse> body = new TFUResponse<>(
                true, data, "Discount type created successfully.", HttpStatus.CREATED.value(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Update discount type")
    @PreAuthorize("@permissionChecker.has('VOUCHER_MANAGEMENT')")
    @PutMapping("/voucher-catalog/{id}")
    public ResponseEntity<TFUResponse<VoucherCatalogResponse>> updateCatalog(
            @PathVariable Long id,
            @Valid @RequestBody SaveVoucherCatalogRequest request) {
        return success(
                voucherAdminService.updateCatalog(id, request), "Discount type updated successfully.");
    }

    @Operation(
            summary = "Enable or disable a discount type",
            description = "Tắt một loại là chặn mọi mã thuộc loại đó tại quầy.")
    @PreAuthorize("@permissionChecker.has('VOUCHER_MANAGEMENT')")
    @PatchMapping("/voucher-catalog/{id}/status")
    public ResponseEntity<TFUResponse<VoucherCatalogResponse>> setCatalogStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return success(
                voucherAdminService.setCatalogStatus(id, status), "Discount type status updated.");
    }

    @Operation(summary = "Delete discount type")
    @PreAuthorize("@permissionChecker.has('VOUCHER_MANAGEMENT')")
    @DeleteMapping("/voucher-catalog/{id}")
    public ResponseEntity<Void> deleteCatalog(@PathVariable Long id) {
        voucherAdminService.deleteCatalog(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Issued codes
    // =========================================================================

    @Operation(summary = "Search, filter and paginate issued codes")
    @PreAuthorize("@permissionChecker.hasAny('VOUCHER_LIST', 'VOUCHER_MANAGEMENT')")
    @GetMapping("/vouchers/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<VoucherAdminResponse>>> getVoucherPage(
            @ModelAttribute PageRequestDTO pageRequest,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long voucherCatalogId,
            @RequestParam(required = false) Long customerId) {
        return successPage(
                voucherAdminService.getVoucherPage(pageRequest, status, voucherCatalogId, customerId));
    }

    @Operation(
            summary = "Issue discount codes",
            description = "Sinh một mã cho khách cụ thể, hoặc một lô mã dùng chung.")
    @PreAuthorize("@permissionChecker.has('VOUCHER_MANAGEMENT')")
    @PostMapping("/vouchers")
    public ResponseEntity<TFUResponse<List<VoucherAdminResponse>>> issueVouchers(
            @Valid @RequestBody IssueVoucherRequest request) {
        List<VoucherAdminResponse> data = voucherAdminService.issueVouchers(request);
        TFUResponse<List<VoucherAdminResponse>> body = new TFUResponse<>(
                true, data, "Discount codes issued successfully.", HttpStatus.CREATED.value(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Revoke an unused discount code")
    @PreAuthorize("@permissionChecker.has('VOUCHER_MANAGEMENT')")
    @PatchMapping("/vouchers/{id}/revoke")
    public ResponseEntity<TFUResponse<VoucherAdminResponse>> revokeVoucher(@PathVariable Long id) {
        return success(voucherAdminService.revokeVoucher(id), "Discount code revoked.");
    }

    @Operation(summary = "Delete an unused discount code")
    @PreAuthorize("@permissionChecker.has('VOUCHER_MANAGEMENT')")
    @DeleteMapping("/vouchers/{id}")
    public ResponseEntity<Void> deleteVoucher(@PathVariable Long id) {
        voucherAdminService.deleteVoucher(id);
        return ResponseEntity.noContent().build();
    }
}
