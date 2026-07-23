package base.api.feature.shiftsession.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HighValueItemResponse {

    private Integer productId;
    private String productName;
    private String categoryName;
    private Integer expectedQty;
    private Integer actualQty;
    private Integer difference;
}
