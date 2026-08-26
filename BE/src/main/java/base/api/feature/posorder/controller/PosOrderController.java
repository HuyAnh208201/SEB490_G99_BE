package base.api.feature.posorder.controller;

import base.api.feature.posorder.dto.request.CheckoutRequest;
import base.api.feature.posorder.dto.response.ApplicablePromotionResponse;
import base.api.feature.posorder.dto.response.OrderResponse;
import base.api.feature.posorder.service.IPosOrderService;
import base.api.feature.product.service.IProductService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.dto.PageResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pos/orders")
@Tag(name = "POS Orders", description = "Bán hàng tại quầy: chốt đơn, lịch sử, mã giảm giá")
public class PosOrderController extends BaseAPIController {

    @Autowired
    private IPosOrderService posOrderService;

    @Autowired
    private IProductService productService;

    @Operation(
            summary = "Chốt đơn tại quầy",
            description = "Ghi hoá đơn, trừ tồn kho và chốt điểm trong cùng một "
                    + "transaction. Giá và tiền giảm đều tính lại ở server — client chỉ gửi "
                    + "productId, số lượng, optional campaignId và số điểm muốn đổi. "
                    + "Loyalty points are earned on the payable amount after promo discount."
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
            summary = "Applicable promotions for the cashier's branch",
            description = "Returns ACTIVE campaigns visible to the current branch. "
                    + "Each item includes eligibility against the given cart subtotal "
                    + "(min order amount / supported discount types) and a computed "
                    + "discountAmount when eligible."
    )
    @PreAuthorize("@permissionChecker.has('POS_CHECKOUT')")
    @GetMapping("/applicable-promotions")
    public ResponseEntity<TFUResponse<List<ApplicablePromotionResponse>>> listApplicablePromotions(
            @RequestParam(required = false) BigDecimal subtotal) {

        return success(posOrderService.listApplicablePromotions(
                subtotal == null ? BigDecimal.ZERO : subtotal));
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

    @Operation(summary = "Paginated order history of the cashier's current shift")
    @PreAuthorize("@permissionChecker.has('POS_CHECKOUT')")
    @GetMapping("/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<OrderResponse>>> getOrderPage(
            PageRequestDTO pageRequest,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String paymentMethod) {

        return successPage(posOrderService.getOrderPage(pageRequest, from, to, paymentMethod));
    }

    @Operation(summary = "Lightweight POS catalog (paged when search/page params present)")
    @PreAuthorize("@permissionChecker.has('POS_CHECKOUT')")
    @GetMapping("/catalog")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ResponseEntity getCatalog(
            @ModelAttribute PageRequestDTO pageRequest,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(defaultValue = "false") boolean paged) {
        if (paged || pageRequest.getSearch() != null || categoryId != null
                || pageRequest.getPage() > 1 || pageRequest.getSize() != PageRequestDTO.DEFAULT_PAGE_SIZE) {
            return successPage(productService.getPosCatalogPage(pageRequest, categoryId));
        }
        return success(productService.getPosCatalog());
    }

    @Operation(summary = "Chi tiết một đơn")
    @PreAuthorize("@permissionChecker.has('POS_CHECKOUT')")
    @GetMapping("/{id}")
    public ResponseEntity<TFUResponse<OrderResponse>> getOrderById(@PathVariable Long id) {
        return success(posOrderService.getOrderById(id));
    }
}
