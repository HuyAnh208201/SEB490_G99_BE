package base.api.feature.posscan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Một mã quét do thiết bị phụ gửi lên (thành công hoặc lỗi). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScanEventResponse {

    private Long id;
    private String barcode;
    private Integer productId;
    private String productName;
    private LocalDateTime createdAt;
    /** Null when the scan succeeded; set when not-found / out-of-stock / etc. */
    private String errorMessage;
    /** True when productId is present and errorMessage is null. */
    private boolean success;
}
