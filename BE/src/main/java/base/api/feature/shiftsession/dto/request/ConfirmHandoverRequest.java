package base.api.feature.shiftsession.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ConfirmHandoverRequest {

    @NotNull
    private BigDecimal actualCash;

    private Long handoverToEmployeeId;

    private String remark;
}
