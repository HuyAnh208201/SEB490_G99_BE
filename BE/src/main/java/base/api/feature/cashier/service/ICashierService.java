package base.api.feature.cashier.service;

import base.api.feature.cashier.dto.request.AddPointsRequest;
import base.api.feature.cashier.dto.request.CreateCustomerRequest;
import base.api.feature.cashier.dto.response.AddPointsResponse;
import base.api.feature.cashier.dto.response.CustomerLookupResponse;

import java.util.List;

public interface ICashierService {

    /**
     * Tra cứu khách hàng theo SĐT hoặc email.
     * Dùng trước khi tích điểm để cashier xác nhận đúng khách.
     */
    CustomerLookupResponse lookupCustomer(String phoneOrEmail);

    /**
     * Tìm khách theo một phần SĐT, email hoặc tên — cashier không phải gõ đủ SĐT.
     * Trả về tối đa {@code SEARCH_LIMIT} kết quả, rỗng nếu không khớp ai.
     */
    List<CustomerLookupResponse> searchCustomers(String keyword);

    /**
     * Tạo nhanh khách mới tại quầy (tên + SĐT). Nếu SĐT đã có khách thì trả về
     * khách đó thay vì tạo trùng.
     */
    CustomerLookupResponse createCustomer(CreateCustomerRequest request);

    /**
     * Cộng điểm tích lũy cho khách hàng từ hóa đơn.
     * Quy tắc: cứ 10.000 VNĐ = 1 điểm.
     */
    AddPointsResponse addPointsFromInvoice(AddPointsRequest request);
}
