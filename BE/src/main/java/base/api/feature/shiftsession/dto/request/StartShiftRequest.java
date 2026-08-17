package base.api.feature.shiftsession.dto.request;

import base.api.shared.enums.FundTransferMethod;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StartShiftRequest {

    /** Cashier acknowledgement that physical opening fund was received offline. */
    private Boolean confirmedReceived;

    private Long receivedFromEmployeeId;

    private FundTransferMethod fundMethod;

    private String note;
}
