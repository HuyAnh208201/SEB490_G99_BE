package base.api.feature.shiftsession.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShiftSessionTransactionSummaryResponse {

    private Integer totalOrders;
    private Integer cashOrders;
    private Integer cardOrders;
    private Integer refundOrders;
    private Integer cancelledOrders;
}
