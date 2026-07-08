package base.api.feature.supplier.mapper;

import base.api.feature.supplier.dto.response.SupplierResponse;
import base.api.shared.entity.SupplierModel;
import org.springframework.stereotype.Component;

@Component
public class SupplierMapper {

    public SupplierResponse toResponse(SupplierModel supplier) {
        SupplierResponse response = new SupplierResponse();
        response.setId(supplier.getId());
        response.setName(supplier.getName());
        response.setContactPerson(supplier.getContactPerson());
        response.setPhone(supplier.getPhone());
        response.setAddress(supplier.getAddress());
        response.setStatus(supplier.getStatus());
        return response;
    }
}
