package base.api.feature.cashier.controller;

import base.api.feature.cashier.dto.request.AddPointsRequest;
import base.api.feature.cashier.dto.request.CreateCustomerRequest;
import base.api.feature.cashier.dto.response.AddPointsResponse;
import base.api.feature.cashier.dto.response.CustomerLookupResponse;
import base.api.feature.cashier.dto.response.LoyaltyConfigResponse;
import base.api.feature.cashier.service.ICashierService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cashier")
@Tag(name = "Cashier", description = "Nghiệp vụ thu ngân: tra cứu khách hàng và tích điểm từ hóa đơn")
public class CashierController extends BaseAPIController {

    @Autowired
    private ICashierService cashierService;

    /**
     * Tra cứu thông tin và điểm tích lũy của khách hàng.
     * Cashier dùng trước khi tính tiền để xác nhận đúng người.
     *
     * GET /api/cashier/customer?phoneOrEmail=0909123456
     */
    @Operation(
            summary = "Tra cứu khách hàng",
            description = "Tìm khách hàng theo SĐT hoặc email. Trả về tên, email và tổng điểm tích lũy."
    )
    @PreAuthorize("@permissionChecker.has('CASHIER_ADD_POINTS')")
    @GetMapping("/customer")
    public ResponseEntity<TFUResponse<CustomerLookupResponse>> lookupCustomer(
            @RequestParam String phoneOrEmail) {

        CustomerLookupResponse customer = cashierService.lookupCustomer(phoneOrEmail);
        return success(customer);
    }

    @Operation(
            summary = "Tỉ lệ tích/đổi điểm",
            description = "FE đọc để hiển thị, không tự đặt. Server luôn tính lại khi chốt đơn."
    )
    @PreAuthorize("@permissionChecker.has('CASHIER_ADD_POINTS')")
    @GetMapping("/loyalty-config")
    public ResponseEntity<TFUResponse<LoyaltyConfigResponse>> loyaltyConfig() {
        return success(cashierService.getLoyaltyConfig());
    }

    /**
     * Tìm khách theo một phần SĐT, email hoặc tên — cashier chỉ cần gõ vài số cuối.
     *
     * GET /api/cashier/customers?keyword=9123
     */
    @Operation(
            summary = "Tìm khách hàng (gõ một phần)",
            description = "Khớp một phần SĐT hoặc tên. Trả về tối đa 10 gợi ý, "
                    + "danh sách rỗng nếu không khớp ai (không phải lỗi 404)."
    )
    @PreAuthorize("@permissionChecker.has('CASHIER_ADD_POINTS')")
    @GetMapping("/customers")
    public ResponseEntity<TFUResponse<List<CustomerLookupResponse>>> searchCustomers(
            @RequestParam String keyword) {

        return success(cashierService.searchCustomers(keyword));
    }

    /**
     * Tạo nhanh khách mới tại quầy khi tra cứu không ra.
     *
     * POST /api/cashier/customer
     */
    @Operation(
            summary = "Tạo nhanh khách hàng",
            description = "Chỉ cần tên và SĐT; email và mật khẩu do hệ thống sinh. "
                    + "Nếu SĐT đã có khách thì trả về khách đó thay vì tạo trùng."
    )
    @PreAuthorize("@permissionChecker.has('CASHIER_ADD_POINTS')")
    @PostMapping("/customer")
    public ResponseEntity<TFUResponse<CustomerLookupResponse>> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request) {

        CustomerLookupResponse customer = cashierService.createCustomer(request);
        return success(customer, "Customer created successfully.");
    }

    /**
     * Chốt điểm cho hóa đơn sau khi thanh toán: trừ điểm khách đổi và cộng điểm kiếm được.
     * Quy tắc cộng: 10.000 VNĐ = 1 điểm. Quy tắc đổi do FE quyết định giá trị quy đổi.
     *
     * POST /api/cashier/add-points
     */
    @Operation(
            summary = "Chốt điểm cho hóa đơn",
            description = "Trừ điểm khách đổi lấy giảm giá (pointsToRedeem) rồi cộng điểm kiếm được "
                    + "từ số tiền thực trả (10.000 VNĐ = 1 điểm). Cả hai chạy trong cùng một "
                    + "transaction nên không thể trừ mà không cộng. Hóa đơn dưới 10.000 VNĐ chỉ "
                    + "đơn giản là được 0 điểm, không phải lỗi."
    )
    @PreAuthorize("@permissionChecker.has('CASHIER_ADD_POINTS')")
    @PostMapping("/add-points")
    public ResponseEntity<TFUResponse<AddPointsResponse>> addPoints(
            @Valid @RequestBody AddPointsRequest request) {

        AddPointsResponse result = cashierService.addPointsFromInvoice(request);
        return success(result, "Loyalty points settled. Earned " + result.getPointsEarned()
                + ", redeemed " + result.getPointsRedeemed() + ".");
    }
}
