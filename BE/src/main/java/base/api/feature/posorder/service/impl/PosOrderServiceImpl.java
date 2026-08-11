package base.api.feature.posorder.service.impl;

import base.api.feature.auth.service.IUserService;
import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.cashier.service.ICashierService;
import base.api.feature.posorder.dto.request.CheckoutLineRequest;
import base.api.feature.posorder.dto.request.CheckoutRequest;
import base.api.feature.posorder.dto.response.OrderItemResponse;
import base.api.feature.posorder.dto.response.OrderResponse;
import base.api.feature.posorder.dto.response.VoucherResponse;
import base.api.feature.posorder.repository.OrderDiscountRepository;
import base.api.feature.posorder.repository.OrderItemRepository;
import base.api.feature.posorder.repository.OrderRepository;
import base.api.feature.posorder.repository.PaymentRepository;
import base.api.feature.posorder.repository.VoucherCatalogRepository;
import base.api.feature.posorder.repository.VoucherRepository;
import base.api.feature.posorder.service.IPosOrderService;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.shift.repository.ShiftRepository;
import base.api.shared.entity.OrderDiscountModel;
import base.api.shared.entity.OrderItemModel;
import base.api.shared.entity.OrderModel;
import base.api.shared.entity.PaymentModel;
import base.api.shared.entity.PointTransactionModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.ShiftModel;
import base.api.shared.entity.UserModel;
import base.api.shared.entity.VoucherCatalogModel;
import base.api.shared.entity.VoucherModel;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.enums.ShiftStatus;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BusinessException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PosOrderServiceImpl implements IPosOrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderDiscountRepository orderDiscountRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private VoucherCatalogRepository voucherCatalogRepository;

    @Autowired
    private IProductRepository productRepository;

    @Autowired
    private BranchInventoryRepository branchInventoryRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private IUserService userService;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private ICashierService cashierService;

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    // =========================================================================
    // Chốt đơn
    // =========================================================================

    @Override
    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {
        UserModel cashier = requireCashier();
        Long branchId = cashier.getBranchId();
        LocalDateTime now = LocalDateTime.now();

        // 1. Gộp dòng trùng sản phẩm trước, nếu không thì kiểm tồn kho từng dòng sẽ
        //    cho qua trong khi tổng số lượng đã vượt kho.
        Map<Integer, Integer> quantityByProduct = mergeLines(request.getLines());
        Map<Integer, ProductModel> products = loadProducts(quantityByProduct.keySet());

        // 2. Giá lấy từ DB, không tin số client gửi.
        List<OrderItemModel> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map.Entry<Integer, Integer> entry : quantityByProduct.entrySet()) {
            ProductModel product = products.get(entry.getKey());
            BigDecimal unitPrice = product.getDefaultSalePrice();
            if (unitPrice == null) {
                throw new BusinessException("Product " + product.getName() + " has no sale price.");
            }
            int quantity = entry.getValue();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

            OrderItemModel item = new OrderItemModel();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setQuantity(quantity);
            item.setUnitPrice(unitPrice);
            item.setLineTotal(lineTotal);
            items.add(item);
            subtotal = subtotal.add(lineTotal);
        }

        // 3. Khách hàng: tạo nhanh nếu SĐT chưa có. Phải có trước voucher vì mã phát
        //    riêng cho khách chỉ hợp lệ khi đúng người đó đứng tên đơn.
        UserModel customer = resolveCustomer(request);

        // 4. Voucher: server tự tính số tiền giảm từ loại voucher.
        VoucherModel voucher = resolveVoucher(request.getVoucherCode());
        assertVoucherBelongsTo(voucher, customer);
        BigDecimal voucherDiscount = voucherDiscountFor(voucher, subtotal);
        BigDecimal afterVoucher = subtotal.subtract(voucherDiscount);

        // 5. Điểm đổi: chặn trên theo số tiền còn lại, không để đổi thừa mất điểm oan.
        long pointsToRedeem = affordablePoints(request.getPointsToRedeem(), customer, afterVoucher);
        BigDecimal pointsDiscount = pointsToRedeem > 0
                ? cashierService.redeemValueOf(pointsToRedeem)
                : BigDecimal.ZERO;
        BigDecimal total = afterVoucher.subtract(pointsDiscount).max(BigDecimal.ZERO);

        validatePayment(request, total);

        // 6. Trừ tồn kho atomic — hết hàng thì cả đơn rollback.
        for (OrderItemModel item : items) {
            int updated = branchInventoryRepository.deductStock(
                    branchId, item.getProductId(), item.getQuantity());
            if (updated == 0) {
                throw new BusinessException(
                        "Not enough stock for " + item.getProductName() + ".");
            }
        }

        // 7. Chốt điểm (trừ điểm đổi + cộng điểm tích trên số tiền thực trả).
        long pointsEarned = 0;
        if (customer != null) {
            ICashierService.PointSettlement settlement =
                    cashierService.settlePoints(customer, total, pointsToRedeem);
            pointsEarned = settlement.pointsEarned();
        }

        // 8. Khoá voucher sau cùng, khi mọi thứ khác đã chắc chắn thành công.
        if (voucher != null && voucherRepository.markUsed(voucher.getId()) == 0) {
            throw new BusinessException("Discount code was just used on another order.");
        }

        boolean isPayOS = "PAYOS".equalsIgnoreCase(request.getPaymentMethod());

        OrderModel order = new OrderModel();
        order.setBranchId(branchId);
        order.setShiftId(findOpenShiftId(branchId, now));
        order.setCashierId(cashier.getId());
        order.setCustomerId(customer == null ? null : customer.getId());
        order.setSubtotal(subtotal);
        order.setDiscountAmount(voucherDiscount.add(pointsDiscount));
        order.setTotal(total);
        order.setPointsRedeemed(pointsToRedeem);
        order.setPointsEarned(pointsEarned);
        // PAYOS: chờ thanh toán QR → webhook sẽ chuyển sang COMPLETED
        order.setStatus(isPayOS ? "PENDING_PAYMENT" : "COMPLETED");
        order.setCreatedAt(now);
        order = orderRepository.save(order);
        order.setInvoiceCode(buildInvoiceCode(order.getId(), now));
        order = orderRepository.save(order);

        for (OrderItemModel item : items) {
            item.setOrderId(order.getId());
        }
        orderItemRepository.saveAll(items);

        if (voucher != null) {
            OrderDiscountModel discount = new OrderDiscountModel();
            discount.setOrderId(order.getId());
            discount.setVoucherId(voucher.getId());
            discount.setCode(voucher.getCode());
            discount.setDiscountAmount(voucherDiscount);
            orderDiscountRepository.save(discount);
        }

        PaymentModel payment = buildPayment(request, order.getId(), total, now);
        paymentRepository.save(payment);

        // Ghi lịch sử tích điểm cho báo cáo — chỉ THÊM log, không đổi logic tính điểm.
        // Phải ghi ở đây (không phải trong settlePoints) vì lúc settlePoints đơn chưa có id.
        if (customer != null) {
            if (pointsEarned > 0) {
                savePointTransaction(customer.getId(), order.getId(), pointsEarned, "EARN", now);
            }
            if (pointsToRedeem > 0) {
                savePointTransaction(customer.getId(), order.getId(), -pointsToRedeem, "REDEEM", now);
            }
        }

        return toResponse(order, items, payment, customer);
    }

    // =========================================================================
    // Lịch sử đơn
    // =========================================================================

    @Override
    public List<OrderResponse> getOrders(LocalDate from, LocalDate to) {
        UserModel cashier = requireCashier();
        List<OrderModel> orders = (from == null || to == null)
                ? orderRepository.findTop50ByBranchIdOrderByCreatedAtDesc(cashier.getBranchId())
                : orderRepository
                        .findByBranchIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                                cashier.getBranchId(), from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        return hydrate(orders);
    }

    @Override
    public Page<OrderResponse> getOrderPage(
            PageRequestDTO pageRequest,
            LocalDate from,
            LocalDate to,
            String paymentMethod
    ) {
        UserModel cashier = requireCashier();
        Specification<OrderModel> spec = (root, query, cb) ->
                cb.equal(root.get("branchId"), cashier.getBranchId());

        if (from != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
        }

        String search = pageRequest.normalizedSearch();
        if (search != null) {
            String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
            Long searchedId = extractNumericId(search);
            spec = spec.and((root, query, cb) -> {
                var customerSubquery = query.subquery(Long.class);
                var customer = customerSubquery.from(UserModel.class);
                customerSubquery.select(customer.get("id"))
                        .where(cb.like(cb.lower(customer.get("fullName")), pattern));
                var textMatch = cb.or(
                        cb.like(cb.lower(root.get("invoiceCode")), pattern),
                        cb.like(cb.lower(root.get("status")), pattern),
                        root.get("customerId").in(customerSubquery));
                return searchedId == null
                        ? textMatch
                        : cb.or(textMatch, cb.equal(root.get("id"), searchedId));
            });
        }

        if (paymentMethod != null && !paymentMethod.isBlank() && !"ALL".equalsIgnoreCase(paymentMethod)) {
            String normalizedMethod = paymentMethod.trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> {
                var paymentSubquery = query.subquery(Long.class);
                var payment = paymentSubquery.from(PaymentModel.class);
                paymentSubquery.select(payment.get("orderId"))
                        .where(cb.equal(cb.upper(payment.get("method")), normalizedMethod));
                return root.get("id").in(paymentSubquery);
            });
        }

        Pageable pageable = pageRequest.toPageable(
                "createdAt",
                Sort.Direction.DESC,
                Set.of("id", "invoiceCode", "total", "status", "createdAt"));
        Page<OrderModel> orders = orderRepository.findAll(spec, pageable);
        return new PageImpl<>(hydrate(orders.getContent()), pageable, orders.getTotalElements());
    }

    @Override
    public OrderResponse getOrderById(Long id) {
        UserModel cashier = requireCashier();
        OrderModel order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found."));
        if (!order.getBranchId().equals(cashier.getBranchId())) {
            throw new BusinessException("This order belongs to another branch.");
        }
        return hydrate(List.of(order)).get(0);
    }

    @Override
    public VoucherResponse lookupVoucher(String code, String customerPhone) {
        VoucherModel voucher = resolveVoucher(code);
        if (voucher == null) {
            throw new NotFoundException("Discount code not found.");
        }
        // Tra cứu là thao tác chỉ đọc: tìm khách theo SĐT chứ không tạo mới như lúc checkout.
        UserModel customer = customerPhone == null || customerPhone.isBlank()
                ? null
                : userRepository.findByPhone(customerPhone.trim().replaceAll("\\s+", "")).orElse(null);
        assertVoucherBelongsTo(voucher, customer);
        VoucherCatalogModel catalog = catalogOf(voucher);

        VoucherResponse response = new VoucherResponse();
        response.setVoucherId(voucher.getId());
        response.setCode(voucher.getCode());
        response.setName(catalog.getName());
        response.setDiscountType(catalog.getDiscountType());
        response.setDiscountValue(catalog.getDiscountValue());
        response.setExpiresAt(voucher.getExpiresAt());
        return response;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private Map<Integer, Integer> mergeLines(List<CheckoutLineRequest> lines) {
        Map<Integer, Integer> merged = new LinkedHashMap<>();
        for (CheckoutLineRequest line : lines) {
            merged.merge(line.getProductId(), line.getQuantity(), Integer::sum);
        }
        return merged;
    }

    private Map<Integer, ProductModel> loadProducts(java.util.Collection<Integer> ids) {
        Map<Integer, ProductModel> products = productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ProductModel::getId, product -> product));
        if (products.size() != ids.size()) {
            throw new NotFoundException("One or more products were not found.");
        }
        return products;
    }

    private VoucherModel resolveVoucher(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        VoucherModel voucher = voucherRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new NotFoundException("Discount code not found."));
        if ("revoked".equalsIgnoreCase(voucher.getStatus())) {
            throw new BusinessException("This discount code has been revoked.");
        }
        if (!"active".equalsIgnoreCase(voucher.getStatus())) {
            throw new BusinessException("This discount code has already been used.");
        }
        if (voucher.getExpiresAt() != null && voucher.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("This discount code has expired.");
        }
        return voucher;
    }

    /**
     * Mã có customer_id là mã phát riêng cho một khách; null nghĩa là ai dùng cũng được.
     * Không kiểm tra ở đây thì mã riêng của khách này áp được lên đơn của khách khác.
     */
    private void assertVoucherBelongsTo(VoucherModel voucher, UserModel customer) {
        if (voucher == null || voucher.getCustomerId() == null) {
            return;
        }
        // Chưa biết khách là ai thì chưa kết luận được mã của người khác — báo đúng
        // việc cần làm, thay vì đổ cho cashier là dùng nhầm mã.
        if (customer == null) {
            throw new BusinessException(
                    "This discount code is issued to a specific customer. Enter their phone number first.");
        }
        if (!voucher.getCustomerId().equals(customer.getId())) {
            throw new BusinessException("This discount code belongs to another customer.");
        }
    }

    private VoucherCatalogModel catalogOf(VoucherModel voucher) {
        if (voucher.getVoucherCatalogId() == null) {
            throw new BusinessException("This discount code is not linked to a discount type.");
        }
        VoucherCatalogModel catalog = voucherCatalogRepository.findById(voucher.getVoucherCatalogId())
                .orElseThrow(() -> new NotFoundException("Discount type not found."));
        // Tắt một loại voucher trong catalog phải chặn được mọi mã thuộc loại đó.
        if (!"active".equalsIgnoreCase(catalog.getStatus())) {
            throw new BusinessException("This discount code is no longer available.");
        }
        return catalog;
    }

    private BigDecimal voucherDiscountFor(VoucherModel voucher, BigDecimal subtotal) {
        if (voucher == null) {
            return BigDecimal.ZERO;
        }
        VoucherCatalogModel catalog = catalogOf(voucher);
        BigDecimal discount = "PERCENT".equalsIgnoreCase(catalog.getDiscountType())
                ? subtotal.multiply(catalog.getDiscountValue())
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                : catalog.getDiscountValue();
        // Không cho giảm quá giá trị đơn.
        return discount.min(subtotal).max(BigDecimal.ZERO);
    }

    private UserModel resolveCustomer(CheckoutRequest request) {
        String phone = request.getCustomerPhone();
        if (phone == null || phone.isBlank()) {
            return null;
        }
        UserModel customer = userService.getOrCreateGuestByPhone(phone, request.getCustomerName());
        if (customer.getRole() != UserRole.CUSTOMER) {
            throw new BusinessException(
                    "This phone belongs to a staff account, not a customer.");
        }
        return customer;
    }

    /** Điểm chỉ được đổi tới mức phủ hết số tiền còn lại; phần thừa giữ nguyên trong tài khoản. */
    private long affordablePoints(Long requested, UserModel customer, BigDecimal amountLeft) {
        long wanted = requested == null ? 0L : requested;
        if (wanted <= 0 || customer == null) {
            return 0L;
        }
        BigDecimal unitValue = cashierService.redeemValueOf(1);
        if (unitValue.compareTo(BigDecimal.ZERO) <= 0) {
            return 0L;
        }
        long byBalance = customer.getPoints() == null ? 0L : customer.getPoints();
        long byAmount = amountLeft.divide(unitValue, 0, RoundingMode.DOWN).longValue();
        return Math.max(0L, Math.min(wanted, Math.min(byBalance, byAmount)));
    }

    private void validatePayment(CheckoutRequest request, BigDecimal total) {
        if (!"CASH".equalsIgnoreCase(request.getPaymentMethod())) {
            return;
        }
        BigDecimal received = request.getCashReceived();
        if (received == null || received.compareTo(total) < 0) {
            throw new BusinessException("Cash received is less than the amount due.");
        }
    }

    private PaymentModel buildPayment(
            CheckoutRequest request, Long orderId, BigDecimal total, LocalDateTime now) {

        boolean isPayOS = "PAYOS".equalsIgnoreCase(request.getPaymentMethod());

        PaymentModel payment = new PaymentModel();
        payment.setOrderId(orderId);
        payment.setMethod(request.getPaymentMethod().toUpperCase());
        payment.setAmount(total);
        // PAYOS: chờ webhook xác nhận → PENDING; CASH: thành công ngay.
        payment.setStatus(isPayOS ? "PENDING" : "SUCCESS");
        payment.setCreatedAt(now);
        if ("CASH".equalsIgnoreCase(request.getPaymentMethod())) {
            payment.setCashReceived(request.getCashReceived());
            payment.setChangeAmount(request.getCashReceived().subtract(total));
        }
        return payment;
    }

    private Long findOpenShiftId(Long branchId, LocalDateTime now) {
        return shiftRepository
                .findByBranchIdAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAsc(
                        branchId, now, now)
                .stream()
                .filter(shift -> shift.getStatus() == ShiftStatus.PUBLISHED)
                .map(ShiftModel::getId)
                .findFirst()
                .orElse(null);
    }

    private String buildInvoiceCode(Long orderId, LocalDateTime now) {
        return "INV-" + now.getYear() + "-" + String.format("%06d", orderId);
    }

    private void savePointTransaction(Long customerId, Long orderId, long points, String type, LocalDateTime at) {
        PointTransactionModel transaction = new PointTransactionModel();
        transaction.setCustomerId(customerId);
        transaction.setOrderId(orderId);
        transaction.setPoints(points);
        transaction.setType(type);
        transaction.setCreatedAt(at);
        pointTransactionRepository.save(transaction);
    }

    private List<OrderResponse> hydrate(List<OrderModel> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> ids = orders.stream().map(OrderModel::getId).toList();
        Map<Long, List<OrderItemModel>> itemsByOrder = orderItemRepository.findByOrderIdIn(ids).stream()
                .collect(Collectors.groupingBy(OrderItemModel::getOrderId));
        Map<Long, PaymentModel> paymentByOrder = paymentRepository.findByOrderIdIn(ids).stream()
                .collect(Collectors.toMap(PaymentModel::getOrderId, p -> p, (first, ignored) -> first));
        Map<Long, UserModel> customerById = userRepository.findAllById(
                        orders.stream()
                                .map(OrderModel::getCustomerId)
                                .filter(java.util.Objects::nonNull)
                                .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(UserModel::getId, customer -> customer));

        return orders.stream()
                .sorted(Comparator.comparing(OrderModel::getCreatedAt).reversed())
                .map(order -> toResponse(
                        order,
                        itemsByOrder.getOrDefault(order.getId(), List.of()),
                        paymentByOrder.get(order.getId()),
                        customerById.get(order.getCustomerId())))
                .toList();
    }

    private OrderResponse toResponse(
            OrderModel order,
            List<OrderItemModel> items,
            PaymentModel payment,
            UserModel customer) {

        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setInvoiceCode(order.getInvoiceCode());
        response.setBranchId(order.getBranchId());
        response.setShiftId(order.getShiftId());
        response.setCashierId(order.getCashierId());
        response.setCustomerId(order.getCustomerId());
        response.setCustomerName(customer == null ? null : customer.getFirstName());
        response.setSubtotal(order.getSubtotal());
        response.setDiscountAmount(order.getDiscountAmount());
        response.setTotal(order.getTotal());
        response.setPointsRedeemed(order.getPointsRedeemed() == null ? 0 : order.getPointsRedeemed());
        response.setPointsEarned(order.getPointsEarned() == null ? 0 : order.getPointsEarned());
        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setLines(items.stream().map(this::toItemResponse).toList());
        response.setItemCount(items.stream().mapToInt(OrderItemModel::getQuantity).sum());
        if (payment != null) {
            response.setPaymentMethod(payment.getMethod());
            response.setCashReceived(payment.getCashReceived());
            response.setChangeAmount(payment.getChangeAmount());
            response.setPaymentStatus(payment.getStatus());
        }
        return response;
    }

    private OrderItemResponse toItemResponse(OrderItemModel item) {
        OrderItemResponse response = new OrderItemResponse();
        response.setId(item.getId());
        response.setProductId(item.getProductId());
        response.setProductName(item.getProductName());
        response.setQuantity(item.getQuantity());
        response.setUnitPrice(item.getUnitPrice());
        response.setLineTotal(item.getLineTotal());
        return response;
    }

    private UserModel requireCashier() {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole role = currentUserProvider.getCurrentUserRole();
        if (role != UserRole.CASHIER) {
            throw new BusinessException("Only cashiers can sell at the counter.");
        }
        if (currentUser.getBranchId() == null) {
            throw new BusinessException("Cashier is not assigned to a branch.");
        }
        return currentUser;
    }

    private Long extractNumericId(String value) {
        String digits = value.replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(digits);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
