package base.api.feature.shiftsession.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CloseInventoryShiftRequest {

    private Integer adjustedProductsCount;
    private Integer damagedProductsCount;
    private Integer missingProductsCount;
    private String closingNote;
}
