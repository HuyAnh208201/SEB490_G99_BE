package base.api.feature.cashier.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.auth.service.IUserService;
import base.api.feature.cashier.dto.request.AddPointsRequest;
import base.api.feature.cashier.dto.request.CreateCustomerRequest;
import base.api.feature.cashier.dto.response.AddPointsResponse;
import base.api.feature.cashier.dto.response.CustomerLookupResponse;
import base.api.feature.cashier.service.ICashierService;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CashierServiceImpl implements ICashierService {

    /** Cứ 10.000 VNĐ thì khách được 1 điểm. */
    private static final long VND_PER_POINT = 10_000L;

    /** Quầy chỉ cần vài gợi ý để chọn, không phải danh bạ. */
    private static final int SEARCH_LIMIT = 10;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IUserService userService;

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

        long pointsToAdd = calculatePoints(request.getInvoiceAmount());
        validatePointsToAdd(pointsToAdd);

        addPointsToCustomer(customer.getId(), pointsToAdd);

        long newTotalPoints = customer.getPoints() + pointsToAdd;
        return toAddPointsResponse(customer, pointsToAdd, newTotalPoints, request.getInvoiceAmount());
    }

    // -------------------------------------------------------------------------
    // Private helpers — mỗi hàm một nhiệm vụ
    // -------------------------------------------------------------------------

    /**
     * Tìm khách hàng theo SĐT hoặc email.
     * Thử tìm theo email trước, nếu không thấy thì thử theo SĐT.
     */
    private UserModel findCustomerByPhoneOrEmail(String phoneOrEmail) {
        UserModel customer = userRepository.findByEmail(phoneOrEmail)
                .orElse(null);

        if (customer == null) {
            customer = userRepository.findByPhone(phoneOrEmail)
                    .orElseThrow(() -> new NotFoundException(
                            "No customer found for: " + phoneOrEmail
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
        return invoiceAmount.longValue() / VND_PER_POINT;
    }

    /**
     * Đảm bảo hóa đơn đủ lớn để được ít nhất 1 điểm.
     */
    private void validatePointsToAdd(long points) {
        if (points <= 0) {
            throw new BadRequestException(
                    "Invoice total is too small. At least " + VND_PER_POINT + " VND is needed to earn 1 point."
            );
        }
    }

    /**
     * Cộng điểm vào tài khoản khách hàng trong DB.
     */
    private void addPointsToCustomer(Long customerId, long pointsToAdd) {
        userRepository.refundPointsAtomic(customerId, pointsToAdd);
    }

    /** Chuyển UserModel → CustomerLookupResponse. */
    private CustomerLookupResponse toCustomerLookupResponse(UserModel customer) {
        return new CustomerLookupResponse(
                customer.getId(),
                customer.getFirstName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getPoints()
        );
    }

    /** Chuyển kết quả tích điểm → AddPointsResponse. */
    private AddPointsResponse toAddPointsResponse(
            UserModel customer,
            long pointsEarned,
            long newTotalPoints,
            BigDecimal invoiceAmount
    ) {
        return new AddPointsResponse(
                customer.getFirstName(),
                customer.getEmail(),
                pointsEarned,
                newTotalPoints,
                invoiceAmount
        );
    }
}
