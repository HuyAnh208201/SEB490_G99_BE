package base.api.feature.voucher.mapper;

import base.api.feature.voucher.dto.response.VoucherAdminResponse;
import base.api.feature.voucher.dto.response.VoucherCatalogResponse;
import base.api.shared.entity.UserModel;
import base.api.shared.entity.VoucherCatalogModel;
import base.api.shared.entity.VoucherModel;
import org.springframework.stereotype.Component;

@Component
public class VoucherAdminMapper {

    public VoucherCatalogResponse toResponse(VoucherCatalogModel catalog, boolean inUse) {
        VoucherCatalogResponse response = new VoucherCatalogResponse();
        response.setId(catalog.getId());
        response.setName(catalog.getName());
        response.setDiscountType(catalog.getDiscountType());
        response.setDiscountValue(catalog.getDiscountValue());
        response.setPointsRequired(catalog.getPointsRequired());
        response.setStatus(catalog.getStatus());
        response.setInUse(inUse);
        return response;
    }

    /**
     * @param catalog  the code's voucher type; may be null when older data lost the link
     * @param customer the customer it is reserved for, null for a shared code
     */
    public VoucherAdminResponse toResponse(
            VoucherModel voucher, VoucherCatalogModel catalog, UserModel customer) {
        VoucherAdminResponse response = new VoucherAdminResponse();
        response.setId(voucher.getId());
        response.setCode(voucher.getCode());
        response.setVoucherCatalogId(voucher.getVoucherCatalogId());
        response.setCustomerId(voucher.getCustomerId());
        response.setStatus(voucher.getStatus());
        response.setExpiresAt(voucher.getExpiresAt());
        response.setCreatedAt(voucher.getCreatedAt());
        if (catalog != null) {
            response.setCatalogName(catalog.getName());
            response.setDiscountType(catalog.getDiscountType());
            response.setDiscountValue(catalog.getDiscountValue());
        }
        if (customer != null) {
            response.setCustomerName(customer.getFullName());
            response.setCustomerPhone(customer.getPhone());
        }
        return response;
    }
}
