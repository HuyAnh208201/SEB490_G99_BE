package base.api.feature.posorder.controller;

import base.api.feature.posorder.dto.request.CheckoutRequest;
import base.api.feature.posorder.dto.response.OrderResponse;
import base.api.feature.posorder.dto.response.VoucherResponse;
import base.api.feature.posorder.service.IPosOrderService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pos/orders")
@Tag(name = "POS Orders", description = "Bán hàng tại quầy: chốt đơn, lịch sử, mã giảm giá")
public class PosOrderController extends BaseAPIController {

    @Autowired
    private IPosOrderService posOrderService;

    @Operation(
            summary = "Chốt đơn tại quầy",
            description = "Ghi hoá đơn, trừ tồn kho, chốt điểm và khoá voucher trong cùng một "
                    + "transaction. Giá và tiền giảm đều tính lại ở server — client chỉ gửi "
                    + "productId, số lượng, mã giảm giá và số điểm muốn đổi."
    )
    @PreAuthorize("@permissionChecker.has('POS_CHECKOUT')")
    @PostMapping
    public ResponseEntity<TFUResponse<OrderResponse>> checkout(
            @Valid @RequestBody CheckoutRequest request) {

        OrderResponse data = posOrderService.checkout(request);
        TFUResponse<OrderResponse> body = new TFUResponse<>(
                true, data, "Order completed successfully.", HttpStatus.CREATED.value(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(
            summary = "Lịch sử đơn của chi nhánh",
            description = "Bỏ trống from/to thì trả 50 đơn gần nhất."
    )
    @PreAuthorize("@permissionChecker.has('POS_CHECKOUT')")
    @GetMapping
    public ResponseEntity<TFUResponse<List<OrderResponse>>> getOrders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return success(posOrderService.getOrders(from, to));
    }

    @Operation(summary = "Chi tiết một đơn")
    @PreAuthorize("@permissionChecker.has('POS_CHECKOUT')")
    @GetMapping("/{id}")
    public ResponseEntity<TFUResponse<OrderResponse>> getOrderById(@PathVariable Long id) {
        return success(posOrderService.getOrderById(id));
    }

    @Operation(
            summary = "Tra mã giảm giá",
            description = "Kiểm tra mã còn hiệu lực và trả về loại giảm giá để cashier xem trước."
    )
    @PreAuthorize("@permissionChecker.has('POS_CHECKOUT')")
    @GetMapping("/vouchers/{code}")
    public ResponseEntity<TFUResponse<VoucherResponse>> lookupVoucher(@PathVariable String code) {
        return success(posOrderService.lookupVoucher(code));
    }
}
