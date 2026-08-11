package base.api.feature.cashier.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.auth.service.IUserService;
import base.api.feature.cashier.dto.request.AddPointsRequest;
import base.api.feature.cashier.dto.request.CreateCustomerRequest;
import base.api.feature.cashier.dto.request.RedeemVoucherRequest;
import base.api.feature.cashier.dto.response.AddPointsResponse;
import base.api.feature.cashier.dto.response.CustomerLookupResponse;
import base.api.feature.cashier.dto.response.LoyaltyConfigResponse;
import base.api.feature.cashier.dto.response.RedeemVoucherResponse;
import base.api.feature.cashier.dto.response.RedeemableVoucherResponse;
import base.api.feature.cashier.service.ICashierService;
import base.api.feature.posorder.repository.VoucherCatalogRepository;
import base.api.feature.posorder.repository.VoucherRepository;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.feature.system.repository.MembershipTierRepository;
import base.api.feature.voucher.service.VoucherCodeGenerator;
import base.api.shared.entity.MembershipTierModel;
import base.api.shared.entity.PointTransactionModel;
import base.api.shared.entity.UserModel;
import base.api.shared.entity.VoucherCatalogModel;
import base.api.shared.entity.VoucherModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CashierServiceImpl implements ICashierService {

    /** Quầy chỉ cần vài gợi ý để chọn, không phải danh bạ. */
    private static final int SEARCH_LIMIT = 10;

    /** Tiêu bao nhiêu VNĐ được 1 điểm. */
    @Value("${loyalty.vnd-per-point:10000}")
    private long vndPerPoint;

    /** 1 điểm đổi được bao nhiêu VNĐ giảm giá. */
    @Value("${loyalty.point-value-vnd:1000}")
    private long pointValueVnd;

    /** Mã đổi từ điểm sống được bao nhiêu ngày. */
    @Value("${voucher.redeem-expiry-days:30}")
    private long voucherExpiryDays;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private VoucherCatalogRepository voucherCatalogRepository;

    @Autowired
    private VoucherCodeGenerator voucherCodeGenerator;

    @Autowired
    private IUserService userService;

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    @Autowired
    private MembershipTierRepository membershipTierRepository;

    // -------------------------------------------------------------------------
    // Public methods
    // -------------------------------------------------------------------------

    @Override
    public CustomerLookupResponse lookupCustomer(String phoneOrEmail) {
        UserModel customer = findCustomerByPhoneOrEmail(phoneOrEmail);
        return toCustomerLookupResponse(customer);
    }

    @Override
    public List<CustomerLookupResponse> searchCustomers(String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return userRepository.searchCustomers(trimmed, PageRequest.of(0, SEARCH_LIMIT)).stream()
                .map(this::toCustomerLookupResponse)
                .toList();
    }

    @Override
    @Transactional
    public CustomerLookupResponse createCustomer(CreateCustomerRequest request) {
        UserModel customer = userService.getOrCreateGuestByPhone(
                request.getPhone(), request.getFullName());

        // SĐT có thể đã thuộc một tài khoản nhân viên — không được biến họ thành khách.
        validateIsCustomerRole(customer);
        return toCustomerLookupResponse(customer);
    }

    @Override
    @Transactional
    public AddPointsResponse addPointsFromInvoice(AddPointsRequest request) {
        UserModel customer = findCustomerByPhoneOrEmail(request.getPhoneOrEmail());
        long requested = request.getPointsToRedeem() == null ? 0L : request.getPointsToRedeem();

        PointSettlement settlement = settlePoints(customer, request.getInvoiceAmount(), requested);

        // Ghi lịch sử tích điểm cho báo cáo — tích điểm rời không qua đơn nên order_id null.
        if (settlement.pointsEarned() > 0) {
            savePointTransaction(customer.getId(), settlement.pointsEarned(), "EARN");
        }
        if (settlement.pointsRedeemed() > 0) {
            savePointTransaction(customer.getId(), -settlement.pointsRedeemed(), "REDEEM");
        }

        return toAddPointsResponse(
                customer,
                settlement.pointsRedeemed(),
                settlement.pointsEarned(),
                settlement.totalPoints(),
                request.getInvoiceAmount());
    }

    @Override
    public LoyaltyConfigResponse getLoyaltyConfig() {
        return new LoyaltyConfigResponse(vndPerPoint, pointValueVnd);
    }

    @Override
    public List<RedeemableVoucherResponse> getRedeemableVouchers() {
        return voucherCatalogRepository.findAllByOrderByIdAsc().stream()
                .filter(catalog -> "active".equalsIgnoreCase(catalog.getStatus()))
                .filter(catalog -> catalog.getPointsRequired() != null && catalog.getPointsRequired() > 0)
                .map(catalog -> {
                    RedeemableVoucherResponse response = new RedeemableVoucherResponse();
                    response.setVoucherCatalogId(catalog.getId());
                    response.setName(catalog.getName());
                    response.setDiscountType(catalog.getDiscountType());
                    response.setDiscountValue(catalog.getDiscountValue());
                    response.setPointsRequired(catalog.getPointsRequired());
                    return response;
                })
                .toList();
    }

    @Override
    @Transactional
    public RedeemVoucherResponse redeemVoucher(RedeemVoucherRequest request) {
        UserModel customer = findCustomerByPhoneOrEmail(request.getCustomerPhone());

        VoucherCatalogModel catalog = voucherCatalogRepository.findById(request.getVoucherCatalogId())
                .orElseThrow(() -> new NotFoundException("Discount type not found."));
        if (!"active".equalsIgnoreCase(catalog.getStatus())) {
            throw new BadRequestException("This discount type is no longer available.");
        }
        int pointsRequired = catalog.getPointsRequired() == null ? 0 : catalog.getPointsRequired();
        if (pointsRequired <= 0) {
            throw new BadRequestException("This discount type cannot be redeemed with points.");
        }

        // Trừ atomic: hai quầy cùng đổi cho một khách thì quầy thiếu điểm khớp 0 row
        // thay vì cả hai cùng trừ và đẩy số dư xuống âm.
        if (userRepository.deductPointsAtomic(customer.getId(), (long) pointsRequired) == 0) {
            throw new BadRequestException("Customer does not have enough points.");
        }
        savePointTransaction(customer.getId(), -pointsRequired, "VOUCHER_REDEEM");

        VoucherModel voucher = new VoucherModel();
        voucher.setCode(voucherCodeGenerator.generate(VoucherCodeGenerator.DEFAULT_PREFIX));
        voucher.setVoucherCatalogId(catalog.getId());
        // customer_id trỏ users.id — cùng ID space với lúc chốt đơn, nếu không thì mã
        // vừa đổi lại bị từ chối vì "thuộc về khách khác".
        voucher.setCustomerId(customer.getId());
        voucher.setStatus("active");
        voucher.setExpiresAt(LocalDateTime.now().plusDays(voucherExpiryDays));
        voucher.setCreatedAt(LocalDateTime.now());
        VoucherModel saved = voucherRepository.save(voucher);

        // deductPointsAtomic là bulk update, không đụng tới entity đã nạp — lấy points
        // từ nó sẽ báo số dư cao hơn thực tế khi hai quầy cùng đổi cho một khách.
        long pointsRemaining = userRepository.findById(customer.getId())
                .map(fresh -> fresh.getPoints() == null ? 0L : fresh.getPoints())
                .orElse(0L);

        RedeemVoucherResponse response = new RedeemVoucherResponse();
        response.setVoucherId(saved.getId());
        response.setCode(saved.getCode());
        response.setName(catalog.getName());
        response.setDiscountType(catalog.getDiscountType());
        response.setDiscountValue(catalog.getDiscountValue());
        response.setExpiresAt(saved.getExpiresAt());
        response.setPointsSpent(pointsRequired);
        response.setPointsRemaining(pointsRemaining);
        return response;
    }

    @Override
    public BigDecimal redeemValueOf(long points) {
        if (points <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(points).multiply(BigDecimal.valueOf(pointValueVnd));
    }

    @Override
    @Transactional
    public PointSettlement settlePoints(UserModel customer, BigDecimal invoiceAmount, long pointsToRedeem) {
        if (pointsToRedeem > 0) {
            // Atomic: 0 row nghĩa là điểm đã bị tiêu ở nơi khác giữa lúc tra cứu và lúc chốt.
            int updated = userRepository.deductPointsAtomic(customer.getId(), pointsToRedeem);
            if (updated == 0) {
                throw new BadRequestException(
                        "Customer does not have enough points to redeem " + pointsToRedeem + ".");
            }
        }

        // Hoá đơn nhỏ hơn một điểm chỉ đơn giản là không được điểm nào — không phải lỗi,
        // nếu ném exception ở đây thì cả việc trừ điểm phía trên cũng bị rollback.
        long pointsEarned = calculatePoints(invoiceAmount);
        if (pointsEarned > 0) {
            addPointsToCustomer(customer.getId(), pointsEarned);
        }

        long totalPoints = customer.getPoints() - pointsToRedeem + pointsEarned;
        return new PointSettlement(pointsToRedeem, pointsEarned, totalPoints);
    }

    // -------------------------------------------------------------------------
    // Private helpers — mỗi hàm một nhiệm vụ
    // -------------------------------------------------------------------------

    /**
     * Tìm khách hàng theo SĐT hoặc email.
     * Thử tìm theo email trước, nếu không thấy thì thử theo SĐT.
     */
    private UserModel findCustomerByPhoneOrEmail(String phoneOrEmail) {
        String key = phoneOrEmail == null ? "" : phoneOrEmail.trim().replaceAll("\\s+", "");
        if (key.isEmpty()) {
            throw new BadRequestException("Phone or email is required.");
        }

        UserModel customer = userRepository.findByEmail(key).orElse(null);

        if (customer == null) {
            customer = userRepository.findByPhone(key)
                    .orElseThrow(() -> new NotFoundException(
                            "No customer found for: " + key
                    ));
        }

        validateIsCustomerRole(customer);
        return customer;
    }

    /**
     * Kiểm tra user có role CUSTOMER không.
     * Chỉ khách hàng mới được tích điểm, nhân viên thì không.
     */
    private void validateIsCustomerRole(UserModel user) {
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new BadRequestException("This phone or email belongs to a staff account, not a customer.");
        }
    }

    /**
     * Tính số điểm từ số tiền hóa đơn.
     * Quy tắc: 10.000 VNĐ = 1 điểm. Phần lẻ dưới 10.000 VNĐ không tính.
     */
    private long calculatePoints(BigDecimal invoiceAmount) {
        if (invoiceAmount == null || vndPerPoint <= 0) {
            return 0L;
        }
        return invoiceAmount.longValue() / vndPerPoint;
    }

    /**
     * Cộng điểm vào tài khoản khách hàng trong DB.
     */
    private void addPointsToCustomer(Long customerId, long pointsToAdd) {
        userRepository.refundPointsAtomic(customerId, pointsToAdd);
    }

    /** Ghi một dòng lịch sử tích/đổi điểm (order_id null vì tích điểm rời không qua đơn). */
    private void savePointTransaction(Long customerId, long points, String type) {
        PointTransactionModel transaction = new PointTransactionModel();
        transaction.setCustomerId(customerId);
        transaction.setOrderId(null);
        transaction.setPoints(points);
        transaction.setType(type);
        transaction.setCreatedAt(LocalDateTime.now());
        pointTransactionRepository.save(transaction);
    }

    /** Chuyển UserModel → CustomerLookupResponse. */
    private CustomerLookupResponse toCustomerLookupResponse(UserModel customer) {
        String tierCode = null;
        String tierName = null;
        Long tierId = customer.getMembershipTierId();
        if (tierId != null) {
            MembershipTierModel tier = membershipTierRepository.findById(tierId).orElse(null);
            if (tier != null) {
                tierCode = tier.getCode();
                tierName = tier.getName();
            }
        }
        return new CustomerLookupResponse(
                customer.getId(),
                customer.getFirstName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getPoints() == null ? 0L : customer.getPoints(),
                tierCode,
                tierName
        );
    }

    /** Chuyển kết quả chốt điểm → AddPointsResponse. */
    private AddPointsResponse toAddPointsResponse(
            UserModel customer,
            long pointsRedeemed,
            long pointsEarned,
            long newTotalPoints,
            BigDecimal invoiceAmount
    ) {
        return new AddPointsResponse(
                customer.getFirstName(),
                customer.getEmail(),
                pointsRedeemed,
                pointsEarned,
                newTotalPoints,
                invoiceAmount
        );
    }
}
