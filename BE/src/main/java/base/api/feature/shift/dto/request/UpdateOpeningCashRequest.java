package base.api.feature.shift.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Branch Manager adjusting the cash float of a shift by hand — holidays, a special
 * float, or a handover that did not match what the system carried forward.
 */
@Getter
@Setter
public class UpdateOpeningCashRequest {

    /**
     * Cash float for the shift. Note: if the previous shift of the same day is closed
     * after this call, its handover amount overwrites the value set here.
     */
    @NotNull(message = "Opening cash is required.")
    @DecimalMin(value = "0", message = "Opening cash must be greater than or equal to 0.")
    private BigDecimal openingCash;
}
