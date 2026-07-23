package base.api.feature.barcode.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BarcodeScanRequest {

    @NotBlank(message = "Barcode is required.")
    @Size(max = 255, message = "Barcode must not exceed 255 characters.")
    private String barcode;
}
