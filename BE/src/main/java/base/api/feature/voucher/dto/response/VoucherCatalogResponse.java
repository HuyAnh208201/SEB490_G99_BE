package base.api.feature.voucher.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class VoucherCatalogResponse {

    private Long id;
    private String name;
    /** PERCENT hoặc FIXED. */
    private String discountType;
    private BigDecimal discountValue;
    private Integer pointsRequired;
    private String status;
    /** Có mã đã phát tham chiếu loại này hay chưa — FE dùng để ẩn nút xoá. */
    private boolean inUse;
}
