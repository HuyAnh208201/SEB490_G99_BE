package base.api.feature.shiftsession.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ConfirmVerificationRequest {

    @NotNull
    private List<HighValueLineRequest> items = new ArrayList<>();

    @Getter
    @Setter
    public static class HighValueLineRequest {
        @NotNull
        private Integer productId;
        @NotNull
        private Integer actualQty;
    }
}
