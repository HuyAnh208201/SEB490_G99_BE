package base.api.feature.dispatch.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateDispatchOrderRequest {

    @NotNull(message = "Request id is required.")
    private Long requestId;

    /** Required (non-empty) when the request contains any short-date category products. */
    private List<Integer> supplierIds;

    @NotBlank(message = "Shipper name is required.")
    private String shipperName;

    @NotBlank(message = "Shipper phone is required.")
    private String shipperPhone;
}
