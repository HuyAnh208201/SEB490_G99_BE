package base.api.feature.cashier.service;

import base.api.feature.cashier.dto.request.AddPointsRequest;
import base.api.feature.cashier.dto.request.CreateCustomerRequest;
import base.api.feature.cashier.dto.response.AddPointsResponse;
import base.api.feature.cashier.dto.response.CustomerLookupResponse;
import base.api.feature.cashier.dto.response.LoyaltyConfigResponse;
import base.api.shared.entity.UserModel;

import java.math.BigDecimal;
import java.util.List;

public interface ICashierService {

    /** Kết quả chốt điểm cho một hoá đơn. */
    record PointSettlement(long pointsRedeemed, long pointsEarned, long totalPoints) {
    }

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

    /** Tỉ lệ tích và đổi điểm do server quyết định. */
    LoyaltyConfigResponse getLoyaltyConfig();

    /** Quy đổi số điểm ra số tiền giảm giá, dùng khi chốt đơn POS. */
    BigDecimal redeemValueOf(long points);

    /**
     * Trừ điểm đổi rồi cộng điểm tích cho một hoá đơn. Dùng chung cho endpoint
     * add-points và luồng chốt đơn POS, phải chạy trong transaction của caller.
     */
    PointSettlement settlePoints(UserModel customer, BigDecimal invoiceAmount, long pointsToRedeem);
}
