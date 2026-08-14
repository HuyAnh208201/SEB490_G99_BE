package base.api.feature.product.service;

import base.api.feature.product.dto.request.ScheduleProductSalePriceRequest;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.repository.ProductSalePriceRepository;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.ProductSalePriceModel;
import base.api.shared.entity.UserModel;
import base.api.shared.exception.BadRequestException;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSalePriceServiceTest {
    @Mock private ProductSalePriceRepository priceRepository;
    @Mock private IProductRepository productRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @InjectMocks private ProductSalePriceService service;

    @Test
    void scheduleRejectsTodaySoPriceCannotChangeMidday() {
        ProductModel product = product();
        when(productRepository.findById(10)).thenReturn(Optional.of(product));
        ScheduleProductSalePriceRequest request = new ScheduleProductSalePriceRequest();
        request.setPrice(new BigDecimal("15000"));
        request.setEffectiveDate(LocalDate.now());

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.schedule(10, request));

        assertEquals("Effective date must be after today.", error.getMessage());
        verify(priceRepository, never()).save(any());
    }

    @Test
    void scheduleStoresFuturePriceAndActor() {
        ProductModel product = product();
        when(productRepository.findById(10)).thenReturn(Optional.of(product));
        UserModel actor = new UserModel();
        actor.setId(7L);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(actor);
        when(priceRepository.save(any())).thenAnswer(inv -> {
            ProductSalePriceModel saved = inv.getArgument(0);
            saved.setId(22L);
            return saved;
        });
        ScheduleProductSalePriceRequest request = new ScheduleProductSalePriceRequest();
        request.setPrice(new BigDecimal("15000"));
        request.setEffectiveDate(LocalDate.now().plusDays(1));

        var response = service.schedule(10, request);

        assertEquals(22L, response.getId());
        assertEquals(7L, response.getCreatedBy());
        assertEquals(new BigDecimal("15000"), response.getPrice());
    }

    @Test
    void effectivePriceUsesLatestApplicableSchedule() {
        ProductModel product = product();
        ProductSalePriceModel scheduled = new ProductSalePriceModel();
        scheduled.setPrice(new BigDecimal("16000"));
        when(priceRepository.findFirstByProductIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                10, LocalDate.now())).thenReturn(Optional.of(scheduled));

        assertEquals(new BigDecimal("16000"), service.effectivePrice(product));
    }

    @Test
    void effectivePricesLoadsCurrentSchedulesInOneBulkQuery() {
        ProductModel first = product();
        ProductModel second = product();
        second.setId(11);
        second.setDefaultSalePrice(new BigDecimal("22000"));
        ProductSalePriceModel scheduled = new ProductSalePriceModel();
        scheduled.setProductId(10);
        scheduled.setPrice(new BigDecimal("16000"));
        scheduled.setEffectiveDate(LocalDate.now());
        when(priceRepository.findEffectivePrices(List.of(10, 11), LocalDate.now()))
                .thenReturn(List.of(scheduled));

        var prices = service.effectivePrices(List.of(first, second));

        assertEquals(new BigDecimal("16000"), prices.get(10));
        assertEquals(new BigDecimal("22000"), prices.get(11));
        verify(priceRepository).findEffectivePrices(List.of(10, 11), LocalDate.now());
    }

    private ProductModel product() {
        ProductModel product = new ProductModel();
        product.setId(10);
        product.setCode("P10");
        product.setReferenceImportPrice(new BigDecimal("8000"));
        product.setDefaultSalePrice(new BigDecimal("12000"));
        return product;
    }
}
