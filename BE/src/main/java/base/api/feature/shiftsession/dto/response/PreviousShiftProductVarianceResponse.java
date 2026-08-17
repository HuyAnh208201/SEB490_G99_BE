package base.api.feature.shiftsession.dto.response;

import lombok.Getter;
import lombok.Setter;

/**
 * High-value product compare between the previous closed cashier shift and the current session.
 */
@Getter
@Setter
public class PreviousShiftProductVarianceResponse {

    private Integer productId;
    private String productName;
    private String categoryName;
    private Integer previousActualQty;
    private Integer currentExpectedQty;
    private Integer currentActualQty;
    /** currentActual (or currentExpected if not counted) minus previousActual. */
    private Integer variance;
}
