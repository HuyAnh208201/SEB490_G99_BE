package base.api.feature.cashier.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.cashier.dto.request.AddPointsRequest;
import base.api.feature.cashier.dto.response.AddPointsResponse;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashierPointsTest {

    private static final String PHONE = "0909123456";

    @Mock
    private IUserRepository userRepository;

    @Mock
    private PointTransactionRepository pointTransactionRepository;

    @InjectMocks
    private CashierServiceImpl service;

    private UserModel customer;

    @BeforeEach
    void setUp() {
        // @Value is not injected in unit tests — unset rates stay 0 and every invoice earns 0 points.
        ReflectionTestUtils.setField(service, "vndPerPoint", 10_000L);
        ReflectionTestUtils.setField(service, "pointValueVnd", 1_000L);

        customer = new UserModel();
        customer.setId(7L);
        customer.setFullName("Trần Bảo");
        customer.setPhone(PHONE);
        customer.setEmail("walkin_" + PHONE + "@guest.chainstore.com");
        customer.setPoints(40L);
        customer.setRole(UserRole.CUSTOMER);
    }

    @Test
    void redeemsAndEarnsInOneSettlement() {
        stubLookup();
        when(userRepository.deductPointsAtomic(7L, 5L)).thenReturn(1);

        AddPointsResponse result = service.addPointsFromInvoice(request(new BigDecimal("250000"), 5L));

        verify(userRepository).deductPointsAtomic(7L, 5L);
        verify(userRepository).refundPointsAtomic(7L, 25L);
        assertEquals(5L, result.getPointsRedeemed());
        assertEquals(25L, result.getPointsEarned());
        assertEquals(60L, result.getTotalPoints()); // 40 - 5 + 25
    }

    @Test
    void refusesToRedeemMorePointsThanTheCustomerHas() {
        stubLookup();
        // Atomic update matching 0 rows means insufficient points.
        when(userRepository.deductPointsAtomic(7L, 999L)).thenReturn(0);

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.addPointsFromInvoice(request(new BigDecimal("250000"), 999L)));

        assertTrue(error.getMessage().contains("enough points"));
        verify(userRepository, never()).refundPointsAtomic(anyLong(), anyLong());
    }

    @Test
    void smallInvoiceEarnsNothingInsteadOfFailing() {
        stubLookup();

        AddPointsResponse result = service.addPointsFromInvoice(request(new BigDecimal("5000"), null));

        assertEquals(0L, result.getPointsEarned());
        assertEquals(40L, result.getTotalPoints());
        verify(userRepository, never()).refundPointsAtomic(anyLong(), anyLong());
        verify(userRepository, never()).deductPointsAtomic(anyLong(), anyLong());
    }

    @Test
    void unknownCustomerIsNotFound() {
        when(userRepository.findByEmail(PHONE)).thenReturn(Optional.empty());
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service.addPointsFromInvoice(request(new BigDecimal("250000"), 0L)));
    }

    @Test
    void boundaryInvoiceEarnsExactlyOnePoint() {
        stubLookup();

        AddPointsResponse result = service.addPointsFromInvoice(request(new BigDecimal("10000"), null));

        assertEquals(1L, result.getPointsEarned());
        assertEquals(41L, result.getTotalPoints());
        verify(userRepository).refundPointsAtomic(7L, 1L);
    }

    @Test
    void earnOnlyWritesEarnTransactionHistory() {
        stubLookup();

        service.addPointsFromInvoice(request(new BigDecimal("20000"), null));

        verify(pointTransactionRepository).save(any());
        verify(userRepository, never()).deductPointsAtomic(anyLong(), anyLong());
    }

    private void stubLookup() {
        when(userRepository.findByEmail(PHONE)).thenReturn(Optional.empty());
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(customer));
    }

    private AddPointsRequest request(BigDecimal invoiceAmount, Long pointsToRedeem) {
        AddPointsRequest request = new AddPointsRequest();
        request.setPhoneOrEmail(PHONE);
        request.setInvoiceAmount(invoiceAmount);
        request.setPointsToRedeem(pointsToRedeem);
        return request;
    }
}
