package base.api.feature.shiftsession.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StartShiftRequest {

    /** Cashier acknowledgement that physical opening fund was received offline. */
    private Boolean confirmedReceived;

    private String note;
}
