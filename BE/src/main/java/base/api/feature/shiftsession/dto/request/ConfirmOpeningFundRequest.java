package base.api.feature.shiftsession.dto.request;

import base.api.shared.enums.FundTransferMethod;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfirmOpeningFundRequest {

    private Long receivedFromEmployeeId;

    private FundTransferMethod fundMethod;

    private String note;
}
