package base.api.feature.posscan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Một mã quét do thiết bị phụ gửi lên. */
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
}
