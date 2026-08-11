package base.api.feature.voucher.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.posorder.repository.VoucherCatalogRepository;
import base.api.feature.posorder.repository.VoucherRepository;
import base.api.feature.voucher.dto.request.IssueVoucherRequest;
import base.api.feature.voucher.dto.request.SaveVoucherCatalogRequest;
import base.api.feature.voucher.dto.response.VoucherAdminResponse;
import base.api.feature.voucher.dto.response.VoucherCatalogResponse;
import base.api.feature.voucher.mapper.VoucherAdminMapper;
import base.api.feature.voucher.service.VoucherCodeGenerator;
import base.api.shared.entity.UserModel;
import base.api.shared.entity.VoucherCatalogModel;
import base.api.shared.entity.VoucherModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ConflictException;
import base.api.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoucherAdminServiceImplTest {

    private static final Long CATALOG_ID = 21L;
    private static final Long CUSTOMER_ID = 10L;

    @Mock private VoucherRepository voucherRepository;
    @Mock private base.api.feature.posorder.repository.OrderDiscountRepository orderDiscountRepository;
    @Mock private VoucherCatalogRepository voucherCatalogRepository;
    @Mock private IUserRepository userRepository;
    @Spy private VoucherAdminMapper voucherAdminMapper = new VoucherAdminMapper();

    @InjectMocks
    private VoucherAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        // Generator thật chạy trên repository đã mock: các test dưới kiểm chính hành vi
        // sinh mã (tiền tố, trùng lặp) nên mock nó đi là mất hết ý nghĩa.
        VoucherCodeGenerator generator = new VoucherCodeGenerator();
        ReflectionTestUtils.setField(generator, "voucherRepository", voucherRepository);
        ReflectionTestUtils.setField(service, "voucherCodeGenerator", generator);

        when(voucherCatalogRepository.save(any())).thenAnswer(call -> {
            VoucherCatalogModel catalog = call.getArgument(0);
            if (catalog.getId() == null) {
                catalog.setId(CATALOG_ID);
            }
            return catalog;
        });
        when(voucherRepository.save(any())).thenAnswer(call -> {
            VoucherModel voucher = call.getArgument(0);
            if (voucher.getId() == null) {
                voucher.setId(99L);
            }
            return voucher;
        });
    }

    // =========================================================================
    // Loại voucher
    // =========================================================================

    @Test
    void percentAboveHundredIsRejected() {
        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.createCatalog(catalogRequest("Giảm nhiều", "PERCENT", "150")));

        assertTrue(error.getMessage().contains("between 0 and 100"));
        verify(voucherCatalogRepository, never()).save(any());
    }

    @Test
    void zeroDiscountValueIsRejected() {
        assertThrows(
                BadRequestException.class,
                () -> service.createCatalog(catalogRequest("Giảm 0", "FIXED", "0")));
    }

    @Test
    void unknownDiscountTypeIsRejected() {
        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.createCatalog(catalogRequest("Lạ", "BUY_X_GET_Y", "10")));

        assertTrue(error.getMessage().contains("PERCENT or FIXED"));
    }

    /** Seed data ghi 'percent'/'fixed' thường — không được từ chối dữ liệu hợp lệ. */
    @Test
    void lowercaseDiscountTypeIsNormalized() {
        VoucherCatalogResponse response =
                service.createCatalog(catalogRequest("Giảm 5%", "percent", "5"));

        assertEquals("PERCENT", response.getDiscountType());
    }

    @Test
    void duplicateCatalogNameIsRejected() {
        when(voucherCatalogRepository.existsByNameIgnoreCase("Giảm 10%")).thenReturn(true);

        assertThrows(
                ConflictException.class,
                () -> service.createCatalog(catalogRequest("Giảm 10%", "PERCENT", "10")));
    }

    @Test
    void catalogStatusMustBeActiveOrInactive() {
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog()));

        assertThrows(BadRequestException.class, () -> service.setCatalogStatus(CATALOG_ID, "paused"));
    }

    @Test
    void deletingCatalogWithIssuedCodesIsRejected() {
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog()));
        when(voucherRepository.existsByVoucherCatalogId(CATALOG_ID)).thenReturn(true);

        ConflictException error =
                assertThrows(ConflictException.class, () -> service.deleteCatalog(CATALOG_ID));

        assertTrue(error.getMessage().contains("inactive"));
        verify(voucherCatalogRepository, never()).delete(any());
    }

    @Test
    void deletingUnusedCatalogSucceeds() {
        VoucherCatalogModel catalog = catalog();
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog));
        when(voucherRepository.existsByVoucherCatalogId(CATALOG_ID)).thenReturn(false);

        service.deleteCatalog(CATALOG_ID);

        verify(voucherCatalogRepository).delete(catalog);
    }

    /**
     * Khách đã cầm mã của loại này (có khi vừa đổi bằng điểm) — đổi mức giảm là đổi
     * giá trị thứ họ đã mua.
     */
    @Test
    void changingDiscountValueOfAnInUseCatalogIsRejected() {
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog()));
        when(voucherRepository.existsByVoucherCatalogId(CATALOG_ID)).thenReturn(true);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.updateCatalog(CATALOG_ID, catalogRequest("Giảm 10.000đ", "FIXED", "1000")));

        assertTrue(error.getMessage().contains("Create a new type"));
        verify(voucherCatalogRepository, never()).save(any());
    }

    /** Vẫn phải đổi được tên và số điểm — chỉ mức giảm là bị khoá. */
    @Test
    void renamingAnInUseCatalogIsAllowed() {
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog()));
        when(voucherRepository.existsByVoucherCatalogId(CATALOG_ID)).thenReturn(true);

        SaveVoucherCatalogRequest request = catalogRequest("Tên mới", "FIXED", "10000");
        request.setPointsRequired(250);

        VoucherCatalogResponse response = service.updateCatalog(CATALOG_ID, request);

        assertEquals("Tên mới", response.getName());
        assertEquals(250, response.getPointsRequired());
    }

    /** Bỏ trống pointsRequired là "giữ nguyên", không phải "gỡ khỏi đổi điểm". */
    @Test
    void omittingPointsRequiredKeepsTheExistingValue() {
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog()));

        VoucherCatalogResponse response =
                service.updateCatalog(CATALOG_ID, catalogRequest("Giảm 10.000đ", "FIXED", "10000"));

        assertEquals(100, response.getPointsRequired());
    }

    // =========================================================================
    // Phát mã
    // =========================================================================

    @Test
    void issuingFromInactiveCatalogIsRejected() {
        VoucherCatalogModel catalog = catalog();
        catalog.setStatus("inactive");
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.issueVouchers(issueRequest(null, 1)));

        assertTrue(error.getMessage().contains("inactive discount type"));
    }

    @Test
    void pastExpiryIsRejected() {
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog()));
        IssueVoucherRequest request = issueRequest(null, 1);
        request.setExpiresAt(LocalDateTime.now().minusDays(1));

        assertThrows(BadRequestException.class, () -> service.issueVouchers(request));
    }

    /**
     * customer_id trỏ users.id — chính là bug ID space đã tìm ra. Không được nhận
     * id của tài khoản không phải khách.
     */
    @Test
    void issuingToNonCustomerAccountIsRejected() {
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog()));
        UserModel cashier = new UserModel();
        cashier.setId(CUSTOMER_ID);
        cashier.setRole(UserRole.CASHIER);
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(cashier));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.issueVouchers(issueRequest(CUSTOMER_ID, 1)));

        assertTrue(error.getMessage().contains("customer accounts"));
        verify(voucherRepository, never()).save(any());
    }

    @Test
    void issuingToUnknownCustomerIsRejected() {
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog()));
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.issueVouchers(issueRequest(CUSTOMER_ID, 1)));
    }

    /** Sinh hàng loạt cho một khách là vô nghĩa — mọi mã đều thuộc về đúng người đó. */
    @Test
    void bulkIssuingToOneCustomerIsRejected() {
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog()));
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.issueVouchers(issueRequest(CUSTOMER_ID, 5)));

        assertTrue(error.getMessage().contains("one at a time"));
    }

    @Test
    void issuingToCustomerStoresTheUserId() {
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog()));
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));

        List<VoucherAdminResponse> issued = service.issueVouchers(issueRequest(CUSTOMER_ID, 1));

        assertEquals(1, issued.size());
        assertEquals(CUSTOMER_ID, issued.get(0).getCustomerId());
        assertEquals("0911111111", issued.get(0).getCustomerPhone());
    }

    @Test
    void bulkIssuingWithoutCustomerCreatesSharedCodes() {
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog()));

        List<VoucherAdminResponse> issued = service.issueVouchers(issueRequest(null, 3));

        assertEquals(3, issued.size());
        assertTrue(issued.stream().allMatch(v -> v.getCustomerId() == null));
        // Mã phải khác nhau, nếu không lô phát ra chỉ dùng được một lần.
        assertEquals(3, issued.stream().map(VoucherAdminResponse::getCode).distinct().count());
    }

    @Test
    void generatedCodeUsesRequestedPrefix() {
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog()));
        IssueVoucherRequest request = issueRequest(null, 1);
        request.setCodePrefix("tet-2027");

        String code = service.issueVouchers(request).get(0).getCode();

        assertTrue(code.startsWith("TET2027"), "Prefix phải được chuẩn hoá, nhận: " + code);
    }

    /** Đụng unique key liên tục là dấu hiệu hỏng, không được ném mã trùng vào DB. */
    @Test
    void givesUpWhenCodeSpaceKeepsColliding() {
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog()));
        when(voucherRepository.existsByCodeIgnoreCase(anyString())).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.issueVouchers(issueRequest(null, 1)));
        verify(voucherRepository, never()).save(any());
    }

    // =========================================================================
    // Thu hồi / xoá mã
    // =========================================================================

    @Test
    void revokingMarksTheCodeRevoked() {
        VoucherModel voucher = voucher("active");
        when(voucherRepository.findById(99L)).thenReturn(Optional.of(voucher));

        VoucherAdminResponse response = service.revokeVoucher(99L);

        assertEquals("revoked", response.getStatus());
    }

    @Test
    void revokingAnAlreadyUsedCodeIsRejected() {
        when(voucherRepository.findById(99L)).thenReturn(Optional.of(voucher("used")));

        assertThrows(BadRequestException.class, () -> service.revokeVoucher(99L));
    }

    @Test
    void revokingTwiceIsRejected() {
        when(voucherRepository.findById(99L)).thenReturn(Optional.of(voucher("revoked")));

        assertThrows(BadRequestException.class, () -> service.revokeVoucher(99L));
    }

    /** Mã đã dùng phải giữ để đối soát với order_discounts. */
    @Test
    void deletingUsedCodeIsRejected() {
        when(voucherRepository.findById(99L)).thenReturn(Optional.of(voucher("used")));

        assertThrows(ConflictException.class, () -> service.deleteVoucher(99L));
        // delete(..) bị nạp chồng bởi JpaSpecificationExecutor nên phải nêu rõ kiểu.
        verify(voucherRepository, never()).delete(any(VoucherModel.class));
    }

    /**
     * Hoàn đơn nhả mã về 'active' trong khi order_discounts vẫn trỏ tới nó — xoá lúc
     * đó sẽ để lại tham chiếu mồ côi trên hoá đơn cũ.
     */
    @Test
    void deletingReleasedCodeStillReferencedByAnOrderIsRejected() {
        when(voucherRepository.findById(99L)).thenReturn(Optional.of(voucher("active")));
        when(orderDiscountRepository.existsByVoucherId(99L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.deleteVoucher(99L));
        verify(voucherRepository, never()).delete(any(VoucherModel.class));
    }

    @Test
    void deletingUnusedCodeSucceeds() {
        VoucherModel voucher = voucher("active");
        when(voucherRepository.findById(99L)).thenReturn(Optional.of(voucher));

        service.deleteVoucher(99L);

        verify(voucherRepository).delete(voucher);
    }

    @Test
    void voucherWithoutCustomerReportsNoCustomerDetails() {
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog()));

        VoucherAdminResponse issued = service.issueVouchers(issueRequest(null, 1)).get(0);

        assertNull(issued.getCustomerId());
        assertNull(issued.getCustomerPhone());
        assertEquals("Giảm 10.000đ", issued.getCatalogName());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private SaveVoucherCatalogRequest catalogRequest(String name, String type, String value) {
        SaveVoucherCatalogRequest request = new SaveVoucherCatalogRequest();
        request.setName(name);
        request.setDiscountType(type);
        request.setDiscountValue(new BigDecimal(value));
        return request;
    }

    private IssueVoucherRequest issueRequest(Long customerId, int quantity) {
        IssueVoucherRequest request = new IssueVoucherRequest();
        request.setVoucherCatalogId(CATALOG_ID);
        request.setCustomerId(customerId);
        request.setQuantity(quantity);
        request.setExpiresAt(LocalDateTime.now().plusDays(30));
        return request;
    }

    private VoucherCatalogModel catalog() {
        VoucherCatalogModel catalog = new VoucherCatalogModel();
        catalog.setId(CATALOG_ID);
        catalog.setName("Giảm 10.000đ");
        catalog.setDiscountType("FIXED");
        catalog.setDiscountValue(new BigDecimal("10000"));
        catalog.setPointsRequired(100);
        catalog.setStatus("active");
        return catalog;
    }

    private UserModel customer() {
        UserModel customer = new UserModel();
        customer.setId(CUSTOMER_ID);
        customer.setFullName("Khách Hàng Một");
        customer.setPhone("0911111111");
        customer.setRole(UserRole.CUSTOMER);
        return customer;
    }

    private VoucherModel voucher(String status) {
        VoucherModel voucher = new VoucherModel();
        voucher.setId(99L);
        voucher.setCode("VCABCDEFGH");
        voucher.setVoucherCatalogId(CATALOG_ID);
        voucher.setStatus(status);
        voucher.setExpiresAt(LocalDateTime.now().plusDays(7));
        return voucher;
    }
}
