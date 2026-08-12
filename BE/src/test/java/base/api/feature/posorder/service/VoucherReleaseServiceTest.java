package base.api.feature.posorder.service;

import base.api.feature.posorder.repository.OrderDiscountRepository;
import base.api.feature.posorder.repository.VoucherRepository;
import base.api.shared.entity.OrderDiscountModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Code release, shared by PAYOS cancellation and approved refunds. */
@ExtendWith(MockitoExtension.class)
class VoucherReleaseServiceTest {

    private static final Long ORDER_ID = 42L;

    @Mock private OrderDiscountRepository orderDiscountRepository;
    @Mock private VoucherRepository voucherRepository;

    @InjectMocks
    private VoucherReleaseService service;

    @Test
    void releasesEveryVoucherAttachedToTheOrder() {
        when(orderDiscountRepository.findByOrderId(ORDER_ID))
                .thenReturn(List.of(discount(11L), discount(12L)));
        when(voucherRepository.markActive(11L)).thenReturn(1);
        when(voucherRepository.markActive(12L)).thenReturn(1);

        assertEquals(2, service.releaseForOrder(ORDER_ID));
    }

    /** A manual discount row carries no code — markActive(null) must never be called. */
    @Test
    void skipsDiscountRowsWithoutVoucherId() {
        when(orderDiscountRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(discount(null)));

        assertEquals(0, service.releaseForOrder(ORDER_ID));
        verify(voucherRepository, never()).markActive(any());
    }

    /**
     * markActive only matches codes at 'used'. Zero rows — already active from a double
     * cancel — must not be counted as a release.
     */
    @Test
    void countsOnlyRowsActuallyFlippedBack() {
        when(orderDiscountRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(discount(11L)));
        when(voucherRepository.markActive(11L)).thenReturn(0);

        assertEquals(0, service.releaseForOrder(ORDER_ID));
    }

    @Test
    void orderWithoutDiscountRowsReleasesNothing() {
        when(orderDiscountRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());

        assertEquals(0, service.releaseForOrder(ORDER_ID));
        verify(voucherRepository, never()).markActive(any());
    }

    private OrderDiscountModel discount(Long voucherId) {
        OrderDiscountModel discount = new OrderDiscountModel();
        discount.setOrderId(ORDER_ID);
        discount.setVoucherId(voucherId);
        discount.setCode("SAVE5K");
        discount.setDiscountAmount(new BigDecimal("5000"));
        return discount;
    }
}
