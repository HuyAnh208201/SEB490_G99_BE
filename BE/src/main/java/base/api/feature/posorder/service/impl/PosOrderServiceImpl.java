package base.api.feature.posorder.service.impl;

import base.api.feature.auth.service.IUserService;
import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.cashier.service.ICashierService;
import base.api.feature.posorder.dto.request.CheckoutLineRequest;
import base.api.feature.posorder.dto.request.CheckoutRequest;
import base.api.feature.posorder.dto.response.ApplicablePromotionResponse;
import base.api.feature.posorder.dto.response.OrderItemResponse;
import base.api.feature.posorder.dto.response.OrderResponse;
import base.api.feature.posorder.repository.OrderDiscountRepository;
import base.api.feature.posorder.repository.OrderItemRepository;
import base.api.feature.posorder.repository.OrderRepository;
import base.api.feature.posorder.repository.PaymentRepository;
import base.api.feature.posorder.service.IPosOrderService;
import base.api.feature.promotion.repository.CampaignRepository;
import base.api.feature.promotion.service.CampaignBranchVisibility;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductCostService;
import base.api.feature.product.service.ProductSalePriceService;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.shift.repository.ShiftRepository;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.CampaignModel;
import base.api.shared.entity.OrderDiscountModel;
import base.api.shared.entity.OrderItemModel;
import base.api.shared.entity.OrderModel;
import base.api.shared.entity.PaymentModel;
import base.api.shared.entity.PointTransactionModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.ShiftModel;
import base.api.shared.entity.UserModel;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.enums.CampaignStatus;
import base.api.shared.enums.CampaignType;
import base.api.shared.enums.ShiftStatus;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BusinessException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PosOrderServiceImpl implements IPosOrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IProductRepository productRepository;

    @Autowired
    private ProductSalePriceService productSalePriceService;

    @Autowired
    private ProductCostService productCostService;

    @Autowired
    private BranchInventoryRepository branchInventoryRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private IUserService userService;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IBranchRepository branchRepository;

    @Autowired
    private ICashierService cashierService;

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CampaignBranchVisibility campaignBranchVisibility;

    @Autowired
    private OrderDiscountRepository orderDiscountRepository;

    @Autowired
    private ObjectMapper objectMapper;

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
            BigDecimal unitPrice = productSalePriceService == null
                    ? product.getDefaultSalePrice() : productSalePriceService.effectivePrice(product);
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
            item.setUnitCost(productCostService.unitCostForProduct(product));
            item.setLineTotal(lineTotal);
            item.setRefundable(!Boolean.FALSE.equals(product.getRefundable()));
            items.add(item);
            subtotal = subtotal.add(lineTotal);
        }

        // 3. Campaign promo (optional) — recomputed server-side; never trust client amounts.
        BigDecimal promoDiscount = BigDecimal.ZERO;
        CampaignModel appliedCampaign = null;
        if (request.getCampaignId() != null) {
            AppliedCampaignDiscount applied = requireEligibleCampaignDiscount(
                    request.getCampaignId(), branchId, subtotal, now);
            appliedCampaign = applied.campaign();
            promoDiscount = applied.discountAmount();
        }
        BigDecimal afterPromo = subtotal.subtract(promoDiscount).max(BigDecimal.ZERO);

        // 4. Khách hàng: tạo nhanh nếu SĐT chưa có.
        UserModel customer = resolveCustomer(request);

        // 5. Điểm đổi: chặn trên theo số tiền còn lại sau promo, không để đổi thừa mất điểm oan.
        long pointsToRedeem = affordablePoints(request.getPointsToRedeem(), customer, afterPromo);
        BigDecimal pointsDiscount = pointsToRedeem > 0
                ? cashierService.redeemValueOf(pointsToRedeem)
                : BigDecimal.ZERO;
        BigDecimal total = afterPromo.subtract(pointsDiscount).max(BigDecimal.ZERO);
        BigDecimal totalDiscount = promoDiscount.add(pointsDiscount);

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

        // 7. Chốt điểm (trừ điểm đổi + cộng điểm tích trên số tiền thực trả sau promo).
        long pointsEarned = 0;
        if (customer != null) {
            ICashierService.PointSettlement settlement =
                    cashierService.settlePoints(customer, total, pointsToRedeem);
            pointsEarned = settlement.pointsEarned();
        }

        boolean isPayOS = "PAYOS".equalsIgnoreCase(request.getPaymentMethod());

        OrderModel order = new OrderModel();
        order.setBranchId(branchId);
        order.setShiftId(findOpenShiftId(branchId, now));
        order.setCashierId(cashier.getId());
        order.setCustomerId(customer == null ? null : customer.getId());
        order.setSubtotal(subtotal);
        order.setDiscountAmount(totalDiscount);
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

        if (appliedCampaign != null && promoDiscount.compareTo(BigDecimal.ZERO) > 0) {
            OrderDiscountModel discountRow = new OrderDiscountModel();
            discountRow.setOrderId(order.getId());
            discountRow.setCode("CAMPAIGN:" + appliedCampaign.getId());
            discountRow.setDiscountAmount(promoDiscount);
            orderDiscountRepository.save(discountRow);
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

        BranchModel branch = branchRepository.findById(branchId).orElse(null);
        return toResponse(order, items, payment, customer, cashier, branch);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicablePromotionResponse> listApplicablePromotions(BigDecimal subtotal) {
        UserModel cashier = requireCashier();
        BigDecimal cartSubtotal = subtotal == null ? BigDecimal.ZERO : subtotal.max(BigDecimal.ZERO);
        return findLiveCampaignsForBranch(cashier.getBranchId(), LocalDateTime.now()).stream()
                .map(campaign -> toApplicablePromotion(campaign, cartSubtotal))
                .toList();
    }

    // =========================================================================
    // Lịch sử đơn
    // =========================================================================

    @Override
    public List<OrderResponse> getOrders(LocalDate from, LocalDate to) {
        UserModel cashier = requireCashier();
        Long shiftId = requireCurrentShiftId(cashier);
        List<OrderModel> orders = (from == null || to == null)
                ? orderRepository.findTop50ByBranchIdAndShiftIdOrderByCreatedAtDesc(
                        cashier.getBranchId(), shiftId)
                : orderRepository
                        .findByBranchIdAndShiftIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                                cashier.getBranchId(), shiftId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
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
        Long shiftId = requireCurrentShiftId(cashier);
        Specification<OrderModel> spec = (root, query, cb) ->
                cb.and(
                        cb.equal(root.get("branchId"), cashier.getBranchId()),
                        cb.equal(root.get("shiftId"), shiftId));

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
        if (!Objects.equals(order.getShiftId(), requireCurrentShiftId(cashier))) {
            throw new BusinessException("This order does not belong to the current shift.");
        }
        return hydrate(List.of(order)).get(0);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private record AppliedCampaignDiscount(CampaignModel campaign, BigDecimal discountAmount) {
    }

    private AppliedCampaignDiscount requireEligibleCampaignDiscount(
            Long campaignId, Long branchId, BigDecimal subtotal, LocalDateTime now) {

        CampaignModel campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Promotion not found."));

        if (campaign.getStatus() != CampaignStatus.ACTIVE
                || campaign.getStartAt() == null
                || campaign.getEndAt() == null
                || campaign.getStartAt().isAfter(now)
                || campaign.getEndAt().isBefore(now)) {
            throw new BusinessException("This promotion is not active.");
        }
        if (!campaignBranchVisibility.isVisibleToBranch(campaign, branchId)) {
            throw new BusinessException("This promotion is not available at your branch.");
        }

        ApplicablePromotionResponse evaluated = toApplicablePromotion(campaign, subtotal);
        if (!evaluated.isEligible()) {
            throw new BusinessException(
                    evaluated.getReason() != null
                            ? evaluated.getReason()
                            : "This promotion cannot be applied to the current cart.");
        }
        return new AppliedCampaignDiscount(campaign, evaluated.getDiscountAmount());
    }

    private List<CampaignModel> findLiveCampaignsForBranch(Long branchId, LocalDateTime now) {
        return campaignRepository.findLiveByStatus(CampaignStatus.ACTIVE, now).stream()
                .filter(campaign -> campaignBranchVisibility.isVisibleToBranch(campaign, branchId))
                .sorted(Comparator
                        .comparing(CampaignModel::getPriority, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CampaignModel::getEndAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }


    private ApplicablePromotionResponse toApplicablePromotion(CampaignModel campaign, BigDecimal subtotal) {
        ApplicablePromotionResponse response = new ApplicablePromotionResponse();
        response.setId(campaign.getId());
        response.setName(campaign.getName());
        response.setType(campaign.getType() == null ? null : campaign.getType().name());
        response.setDiscountValue(campaign.getDiscountValue());

        BigDecimal minOrderAmount = parseMinOrderAmount(campaign.getConditions());
        response.setMinOrderAmount(minOrderAmount);

        if (campaign.getType() == CampaignType.BUY_X_GET_Y || campaign.getType() == null) {
            response.setEligible(false);
            response.setReason("This promotion type is not supported at POS.");
            return response;
        }
        if (campaign.getType() != CampaignType.PERCENT && campaign.getType() != CampaignType.FIXED_AMOUNT) {
            response.setEligible(false);
            response.setReason("This promotion type is not supported at POS.");
            return response;
        }
        if (minOrderAmount != null && subtotal.compareTo(minOrderAmount) < 0) {
            response.setEligible(false);
            response.setReason("Cart subtotal is below the minimum order amount.");
            return response;
        }

        BigDecimal discount = computeCampaignDiscount(campaign.getType(), campaign.getDiscountValue(), subtotal);
        if (discount.compareTo(BigDecimal.ZERO) <= 0) {
            response.setEligible(false);
            response.setReason("This promotion does not reduce the order total.");
            return response;
        }

        response.setEligible(true);
        response.setDiscountAmount(discount);
        return response;
    }

    private BigDecimal computeCampaignDiscount(
            CampaignType type, BigDecimal discountValue, BigDecimal subtotal) {

        if (discountValue == null || subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal safeSubtotal = subtotal.max(BigDecimal.ZERO);
        if (type == CampaignType.PERCENT) {
            BigDecimal raw = safeSubtotal
                    .multiply(discountValue)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return raw.min(safeSubtotal).max(BigDecimal.ZERO);
        }
        if (type == CampaignType.FIXED_AMOUNT) {
            return discountValue.min(safeSubtotal).max(BigDecimal.ZERO);
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal parseMinOrderAmount(String conditionsJson) {
        if (conditionsJson == null || conditionsJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(conditionsJson);
            JsonNode node = root.get("minOrderAmount");
            if (node == null || node.isNull()) {
                return null;
            }
            BigDecimal value = node.isNumber()
                    ? node.decimalValue()
                    : new BigDecimal(node.asText().trim());
            return value.compareTo(BigDecimal.ZERO) > 0 ? value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

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

    private UserModel resolveCustomer(CheckoutRequest request) {
        String phone = request.getCustomerPhone();
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String normalized = phone.trim().replaceAll("\\s+", "");
        if (!normalized.matches("^[0-9+][0-9]{7,19}$")) {
            throw new BusinessException("Customer phone format is invalid.");
        }
        UserModel customer = userService.getOrCreateGuestByPhone(normalized, request.getCustomerName());
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

    private Long requireCurrentShiftId(UserModel cashier) {
        Long shiftId = findOpenShiftId(cashier.getBranchId(), LocalDateTime.now());
        if (shiftId == null) {
            throw new BusinessException("Open a shift before viewing order history.");
        }
        return shiftId;
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
        Set<Long> userIds = new HashSet<>();
        Set<Long> branchIds = new HashSet<>();
        for (OrderModel order : orders) {
            if (order.getCustomerId() != null) {
                userIds.add(order.getCustomerId());
            }
            if (order.getCashierId() != null) {
                userIds.add(order.getCashierId());
            }
            if (order.getBranchId() != null) {
                branchIds.add(order.getBranchId());
            }
        }
        Map<Long, UserModel> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(UserModel::getId, user -> user));
        Map<Long, BranchModel> branchesById = branchIds.isEmpty()
                ? Map.of()
                : branchRepository.findAllById(branchIds).stream()
                        .collect(Collectors.toMap(BranchModel::getId, branch -> branch));

        return orders.stream()
                .sorted(Comparator.comparing(OrderModel::getCreatedAt).reversed())
                .map(order -> toResponse(
                        order,
                        itemsByOrder.getOrDefault(order.getId(), List.of()),
                        paymentByOrder.get(order.getId()),
                        usersById.get(order.getCustomerId()),
                        usersById.get(order.getCashierId()),
                        branchesById.get(order.getBranchId())))
                .toList();
    }

    private OrderResponse toResponse(
            OrderModel order,
            List<OrderItemModel> items,
            PaymentModel payment,
            UserModel customer,
            UserModel cashier,
            BranchModel branch) {

        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setInvoiceCode(order.getInvoiceCode());
        response.setBranchId(order.getBranchId());
        if (branch != null) {
            response.setBranchName(branch.getName());
            response.setBranchAddress(branch.getAddress());
            response.setBranchPhone(branch.getPhone());
        }
        response.setShiftId(order.getShiftId());
        response.setCashierId(order.getCashierId());
        response.setCashierName(cashier == null ? null : cashier.getFullName());
        response.setCustomerId(order.getCustomerId());
        response.setCustomerName(customer == null ? null : customer.getFullName());
        response.setCustomerPhone(customer == null ? null : customer.getPhone());
        response.setSubtotal(order.getSubtotal());
        response.setDiscountAmount(order.getDiscountAmount());
        response.setTotal(order.getTotal());
        response.setPointsRedeemed(order.getPointsRedeemed() == null ? 0 : order.getPointsRedeemed());
        response.setPointsEarned(order.getPointsEarned() == null ? 0 : order.getPointsEarned());
        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setLines(items.stream().map(this::toItemResponse).toList());
        response.setRefundable(items.stream().allMatch(item -> !Boolean.FALSE.equals(item.getRefundable())));
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
        response.setRefundable(!Boolean.FALSE.equals(item.getRefundable()));
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
