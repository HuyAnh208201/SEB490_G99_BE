package base.api.feature.shiftsession.dto.response;

import base.api.shared.enums.ShiftSessionApprovalDecision;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ShiftSessionApprovalHistoryResponse {

    private Long id;
    private ShiftSessionApprovalDecision decision;
    private String note;
    private Long decidedBy;
    private String decidedByName;
    private LocalDateTime decidedAt;
}
