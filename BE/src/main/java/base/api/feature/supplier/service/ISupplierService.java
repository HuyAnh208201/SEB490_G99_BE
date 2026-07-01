package base.api.feature.supplier.service;

import base.api.feature.supplier.dto.request.CreateSupplierRequest;
import base.api.feature.supplier.dto.request.UpdateSupplierRequest;
import base.api.feature.supplier.dto.response.SupplierResponse;

import java.util.List;

public interface ISupplierService {

    SupplierResponse create(CreateSupplierRequest request);

    SupplierResponse update(Integer id, UpdateSupplierRequest request);

    void delete(Integer id);

    SupplierResponse getById(Integer id);

    List<SupplierResponse> getAll();
}
