package base.api.feature.supplier.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupplierResponse {

    private Integer id;
    private String name;
    private String contactPerson;
    private String phone;
    private String address;
    private String status;
}
