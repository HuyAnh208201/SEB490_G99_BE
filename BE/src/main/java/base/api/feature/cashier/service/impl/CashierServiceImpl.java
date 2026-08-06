package base.api.feature.cashier.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.auth.service.IUserService;
import base.api.feature.cashier.dto.request.AddPointsRequest;
import base.api.feature.cashier.dto.request.CreateCustomerRequest;
import base.api.feature.cashier.dto.response.AddPointsResponse;
import base.api.feature.cashier.dto.response.CustomerLookupResponse;
import base.api.feature.cashier.dto.response.LoyaltyConfigResponse;
import base.api.feature.cashier.service.ICashierService;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.feature.system.repository.MembershipTierRepository;
import base.api.shared.entity.MembershipTierModel;
import base.api.shared.entity.PointTransactionModel;
import base.api.shared.entity.UserModel;
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

    @Autowired
    private IUserRepository userRepository;

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

        String email = request.getEmail() == null ? null : request.getEmail().trim();
        if (email != null && !email.isEmpty()) {
            // Chỉ gắn email thật khi tài khoản vẫn đang dùng email walk-in hệ thống sinh.
            String current = customer.getEmail();
            if (current == null || current.endsWith("@guest.chainstore.com")) {
                if (userRepository.findByEmail(email).filter(u -> !u.getId().equals(customer.getId())).isPresent()) {
                    throw new BadRequestException("This email is already in use.");
                }
                customer.setEmail(email);
                userRepository.save(customer);
            }
        }

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
