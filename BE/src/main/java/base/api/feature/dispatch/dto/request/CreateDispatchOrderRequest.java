package base.api.feature.dispatch.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateDispatchOrderRequest {

    @NotNull(message = "Request id is required.")
    private Long requestId;
}
