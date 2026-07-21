package base.api.feature.cashier.service;

import base.api.feature.cashier.dto.request.AddPointsRequest;
import base.api.feature.cashier.dto.response.AddPointsResponse;
import base.api.feature.cashier.dto.response.CustomerLookupResponse;

public interface ICashierService {

    /**
     * Tra cứu khách hàng theo SĐT hoặc email.
     * Dùng trước khi tích điểm để cashier xác nhận đúng khách.
     */
    CustomerLookupResponse lookupCustomer(String phoneOrEmail);

    /**
     * Cộng điểm tích lũy cho khách hàng từ hóa đơn.
     * Quy tắc: cứ 10.000 VNĐ = 1 điểm.
     */
    AddPointsResponse addPointsFromInvoice(AddPointsRequest request);
}
