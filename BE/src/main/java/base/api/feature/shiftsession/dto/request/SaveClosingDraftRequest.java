package base.api.feature.shiftsession.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SaveClosingDraftRequest {

    private BigDecimal actualCash;
    private String handoverRemark;
    private String closingNote;
    private Integer adjustedProductsCount;
    private Integer damagedProductsCount;
    private Integer missingProductsCount;
}
