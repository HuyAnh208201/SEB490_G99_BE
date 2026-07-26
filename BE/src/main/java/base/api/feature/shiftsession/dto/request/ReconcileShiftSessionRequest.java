package base.api.feature.shiftsession.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReconcileShiftSessionRequest {

    @NotNull
    private Boolean approved;

    /** Required when rejecting; optional when approving. */
    private String note;
}
