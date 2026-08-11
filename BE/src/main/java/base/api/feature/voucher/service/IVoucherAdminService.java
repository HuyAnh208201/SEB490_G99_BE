package base.api.feature.voucher.service;

import base.api.feature.voucher.dto.request.IssueVoucherRequest;
import base.api.feature.voucher.dto.request.SaveVoucherCatalogRequest;
import base.api.feature.voucher.dto.response.VoucherAdminResponse;
import base.api.feature.voucher.dto.response.VoucherCatalogResponse;
import base.api.shared.dto.PageRequestDTO;
import org.springframework.data.domain.Page;

import java.util.List;

/** Quản lý loại voucher và các mã đã phát (Admin / Director). */
public interface IVoucherAdminService {

    // ---- Loại voucher ----

    List<VoucherCatalogResponse> getAllCatalogs();

    Page<VoucherCatalogResponse> getCatalogPage(PageRequestDTO pageRequest);

    VoucherCatalogResponse getCatalog(Long id);

    VoucherCatalogResponse createCatalog(SaveVoucherCatalogRequest request);

    VoucherCatalogResponse updateCatalog(Long id, SaveVoucherCatalogRequest request);

    /** Bật/tắt một loại voucher. Tắt là chặn mọi mã thuộc loại đó tại quầy. */
    VoucherCatalogResponse setCatalogStatus(Long id, String status);

    /** Chỉ xoá được loại chưa phát mã nào; còn mã thì phải dùng {@link #setCatalogStatus}. */
    void deleteCatalog(Long id);

    // ---- Mã đã phát ----

    Page<VoucherAdminResponse> getVoucherPage(
            PageRequestDTO pageRequest, String status, Long voucherCatalogId, Long customerId);

    /** Sinh 1 hoặc nhiều mã. Mã phát riêng cho khách chỉ được sinh đúng 1. */
    List<VoucherAdminResponse> issueVouchers(IssueVoucherRequest request);

    /** Thu hồi mã chưa dùng bằng cách cho hết hạn ngay, giữ lại để đối soát. */
    VoucherAdminResponse revokeVoucher(Long id);

    /** Chỉ xoá được mã chưa dùng; mã đã dùng phải giữ để khớp với order_discounts. */
    void deleteVoucher(Long id);
}
