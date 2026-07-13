package base.api.feature.cashier.controller;

import base.api.feature.cashier.dto.request.AddPointsRequest;
import base.api.feature.cashier.dto.response.AddPointsResponse;
import base.api.feature.cashier.dto.response.CustomerLookupResponse;
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

    /**
     * Cộng điểm cho khách hàng sau khi thanh toán hóa đơn.
     * Quy tắc: 10.000 VNĐ = 1 điểm.
     *
     * POST /api/cashier/add-points
     */
    @Operation(
            summary = "Tích điểm từ hóa đơn",
            description = "Cashier nhập SĐT/email khách và số tiền hóa đơn. Hệ thống tính và cộng điểm tự động (10.000 VNĐ = 1 điểm)."
    )
    @PreAuthorize("@permissionChecker.has('CASHIER_ADD_POINTS')")
    @PostMapping("/add-points")
    public ResponseEntity<TFUResponse<AddPointsResponse>> addPoints(
            @Valid @RequestBody AddPointsRequest request) {

        AddPointsResponse result = cashierService.addPointsFromInvoice(request);
        return success(result, "Tích điểm thành công! Khách hàng nhận được " + result.getPointsEarned() + " điểm.");
    }
}
