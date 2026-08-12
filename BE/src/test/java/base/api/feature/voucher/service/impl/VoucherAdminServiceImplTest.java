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
        // The real generator runs against a mocked repository: the tests below check the
        // generation behaviour itself (prefix, collisions), so mocking it would prove nothing.
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
    // Voucher types
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

    /** Seed data stores lower-case 'percent'/'fixed' — valid data must not be refused. */
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
     * Customers already hold codes of this type, some bought with points, so changing the
     * discount changes what they paid for.
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

    /** Name and point price stay editable — only the discount is locked. */
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

    /** An empty pointsRequired means "leave as is", not "remove from point redemption". */
    @Test
    void omittingPointsRequiredKeepsTheExistingValue() {
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog()));

        VoucherCatalogResponse response =
                service.updateCatalog(CATALOG_ID, catalogRequest("Giảm 10.000đ", "FIXED", "10000"));

        assertEquals(100, response.getPointsRequired());
    }

    // =========================================================================
    // Issuing codes
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
     * customer_id points at users.id — the id-space bug this pins down. An account that is
     * not a customer must be rejected.
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

    /** Batch-generating for one customer is pointless — every code would belong to them. */
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
        // The codes must differ, otherwise a whole batch is usable only once.
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

    /** Repeated unique-key collisions signal a fault; never push a duplicate to the database. */
    @Test
    void givesUpWhenCodeSpaceKeepsColliding() {
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog()));
        when(voucherRepository.existsByCodeIgnoreCase(anyString())).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.issueVouchers(issueRequest(null, 1)));
        verify(voucherRepository, never()).save(any());
    }

    // =========================================================================
    // Revoking and deleting codes
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

    /** A used code is kept so it can be reconciled against order_discounts. */
    @Test
    void deletingUsedCodeIsRejected() {
        when(voucherRepository.findById(99L)).thenReturn(Optional.of(voucher("used")));

        assertThrows(ConflictException.class, () -> service.deleteVoucher(99L));
        // delete(..) is overloaded by JpaSpecificationExecutor, so the type must be explicit.
        verify(voucherRepository, never()).delete(any(VoucherModel.class));
    }

    /**
     * A refund returns the code to 'active' while order_discounts still points at it, so
     * deleting then would orphan a reference on an old invoice.
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
