package base.api.feature.shiftsession.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InventoryClosingSummaryResponse {

    private Integer totalSku;
    private Integer lowStockSku;
    private Integer countSessionsDuringShift;
}
