package base.api.feature.voucher.service;

import base.api.feature.voucher.dto.request.IssueVoucherRequest;
import base.api.feature.voucher.dto.request.SaveVoucherCatalogRequest;
import base.api.feature.voucher.dto.response.VoucherAdminResponse;
import base.api.feature.voucher.dto.response.VoucherCatalogResponse;
import base.api.shared.dto.PageRequestDTO;
import org.springframework.data.domain.Page;

import java.util.List;

/** Manages voucher types and issued codes (Admin / Director). */
public interface IVoucherAdminService {

    // ---- Voucher types ----

    List<VoucherCatalogResponse> getAllCatalogs();

    Page<VoucherCatalogResponse> getCatalogPage(PageRequestDTO pageRequest);

    VoucherCatalogResponse getCatalog(Long id);

    VoucherCatalogResponse createCatalog(SaveVoucherCatalogRequest request);

    VoucherCatalogResponse updateCatalog(Long id, SaveVoucherCatalogRequest request);

    /** Enables or disables a type. Disabling blocks every code of that type at the counter. */
    VoucherCatalogResponse setCatalogStatus(Long id, String status);

    /** Only a type with no issued codes can be deleted; otherwise use {@link #setCatalogStatus}. */
    void deleteCatalog(Long id);

    // ---- Issued codes ----

    Page<VoucherAdminResponse> getVoucherPage(
            PageRequestDTO pageRequest, String status, Long voucherCatalogId, Long customerId);

    /** Generates one or many codes. A code reserved for a customer is always exactly one. */
    List<VoucherAdminResponse> issueVouchers(IssueVoucherRequest request);

    /** Revokes an unused code by expiring it now, keeping the row for reconciliation. */
    VoucherAdminResponse revokeVoucher(Long id);

    /** Only an unused code can be deleted; a used one must stay to match order_discounts. */
    void deleteVoucher(Long id);
}
