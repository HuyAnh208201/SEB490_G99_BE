package base.api.feature.supplier.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateSupplierRequest {

    @NotBlank(message = "Supplier name is required.")
    @Size(max = 255, message = "Supplier name must not exceed 255 characters.")
    private String name;

    private String contactPerson;

    @Pattern(regexp = "^[0-9]{10,15}$", message = "Invalid phone number.")
    private String phone;

    private String address;

    @NotBlank(message = "Status is required.")
    @Size(max = 255, message = "Status must not exceed 255 characters.")
    private String status;
}
