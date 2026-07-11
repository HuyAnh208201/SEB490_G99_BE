package base.api.feature.dispatch.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateDispatchOrderRequest {

    @NotEmpty(message = "At least one request must be selected.")
    private List<Long> requestIds;

    private String vehicle;
}
