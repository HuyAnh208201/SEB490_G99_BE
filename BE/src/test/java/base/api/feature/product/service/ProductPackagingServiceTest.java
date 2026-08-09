package base.api.feature.product.service;

import base.api.feature.product.repository.ProductPackagingRepository;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.ProductPackagingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TOP/BASE packaging conversion helpers and null/boundary cases.
 */
@ExtendWith(MockitoExtension.class)
class ProductPackagingServiceTest {

    @Mock
    private ProductPackagingRepository packagingRepository;

    @InjectMocks
    private ProductPackagingService service;

    @Test
    void getTopPackagingReturnsNullForNullProduct() {
        assertNull(service.getTopPackaging(null));
    }

    @Test
    void getTopPackagingReturnsNullWhenProductIdMissing() {
        ProductModel product = new ProductModel();
        product.setId(null);

        assertNull(service.getTopPackaging(product));
        verify(packagingRepository, never()).findFirstByProductIdAndIsPurchaseDefaultTrue(any());
    }

    @Test
    void getTopPackagingUsesPurchaseDefaultRow() {
        ProductModel product = product(5, "bottle", "case", 24);
        ProductPackagingModel top = packaging(5, 24, "Case of 24");
        when(packagingRepository.findFirstByProductIdAndIsPurchaseDefaultTrue(5))
                .thenReturn(Optional.of(top));

        ProductPackagingModel result = service.getTopPackaging(product);

        assertEquals(24, result.getConversionQty());
        assertEquals("Case of 24", result.getLabelEn());
    }

    @Test
    void getTopPackagingFallbackSynthesizesMultiLevel() {
        ProductModel product = product(5, "bottle", "case", 24);
        when(packagingRepository.findFirstByProductIdAndIsPurchaseDefaultTrue(5))
                .thenReturn(Optional.empty());

        ProductPackagingModel result = service.getTopPackaging(product);

        assertEquals(24, result.getConversionQty());
        assertEquals("Case of 24", result.getLabelEn());
        assertFalse(Boolean.TRUE.equals(result.getIsBase()));
    }

    @Test
    void getTopPackagingFallbackSynthesizesSingleLevel() {
        ProductModel product = product(5, "bottle", null, 1);
        when(packagingRepository.findFirstByProductIdAndIsPurchaseDefaultTrue(5))
                .thenReturn(Optional.empty());

        ProductPackagingModel result = service.getTopPackaging(product);

        assertEquals(1, result.getConversionQty());
        assertEquals("Bottle", result.getLabelEn());
        assertTrue(Boolean.TRUE.equals(result.getIsBase()));
    }

    @Test
    void getTopPackagingsByProductIdsReturnsEmptyForNullOrEmpty() {
        assertTrue(service.getTopPackagingsByProductIds(null).isEmpty());
        assertTrue(service.getTopPackagingsByProductIds(List.of()).isEmpty());
        verify(packagingRepository, never()).findByProductIdInAndIsPurchaseDefaultTrue(anyCollection());
    }

    @Test
    void getTopPackagingsByProductIdsKeepsFirstPerProduct() {
        ProductPackagingModel first = packaging(1, 12, "Pack");
        ProductPackagingModel second = packaging(1, 24, "Case");
        when(packagingRepository.findByProductIdInAndIsPurchaseDefaultTrue(List.of(1, 2)))
                .thenReturn(List.of(first, second, packaging(2, 6, "Box")));

        Map<Integer, ProductPackagingModel> map = service.getTopPackagingsByProductIds(List.of(1, 2));

        assertEquals(2, map.size());
        assertEquals(12, map.get(1).getConversionQty());
        assertEquals(6, map.get(2).getConversionQty());
    }

    @Test
    void conversionQtyOfNullOrInvalidDefaultsToOne() {
        assertEquals(1, service.conversionQtyOf(null));

        ProductPackagingModel zero = new ProductPackagingModel();
        zero.setConversionQty(0);
        assertEquals(1, service.conversionQtyOf(zero));

        ProductPackagingModel negative = new ProductPackagingModel();
        negative.setConversionQty(-2);
        assertEquals(1, service.conversionQtyOf(negative));

        ProductPackagingModel missing = new ProductPackagingModel();
        missing.setConversionQty(null);
        assertEquals(1, service.conversionQtyOf(missing));
    }

    @Test
    void conversionQtyOfValidValue() {
        assertEquals(24, service.conversionQtyOf(packaging(1, 24, "Case")));
    }

    @Test
    void toBaseQtyClampsNegativeTopUnitsToZero() {
        assertEquals(0, service.toBaseQty(-5, packaging(1, 24, "Case")));
    }

    @Test
    void toBaseQtyZeroTopUnitsReturnsZero() {
        assertEquals(0, service.toBaseQty(0, packaging(1, 24, "Case")));
    }

    @Test
    void toBaseQtyNullPackagingUsesConversionOne() {
        assertEquals(5, service.toBaseQty(5, (ProductPackagingModel) null));
    }

    @Test
    void toBaseQtyNullProductUsesConversionOne() {
        assertEquals(3, service.toBaseQty(3, (ProductModel) null));
    }

    @Test
    void conversionQtyOfOneBoundary() {
        ProductPackagingModel one = packaging(1, 1, "Bottle");
        assertEquals(1, service.conversionQtyOf(one));
        assertEquals(7, service.toBaseQty(7, one));
    }

    @Test
    void toBaseQtyMultipliesByConversion() {
        assertEquals(48, service.toBaseQty(2, packaging(1, 24, "Case")));
    }

    @Test
    void toBaseQtyViaProductUsesTopPackaging() {
        ProductModel product = product(5, "bottle", "case", 24);
        when(packagingRepository.findFirstByProductIdAndIsPurchaseDefaultTrue(5))
                .thenReturn(Optional.of(packaging(5, 24, "Case of 24")));

        assertEquals(72, service.toBaseQty(3, product));
        assertEquals(24, service.topConversionQty(product));
    }

    @Test
    void topLabelReturnsNullWhenNoPackaging() {
        assertNull(service.topLabel(null));
    }

    @Test
    void topLabelPrefersLabelEnThenName() {
        ProductModel product = product(5, "bottle", "case", 24);
        ProductPackagingModel top = packaging(5, 24, "Case of 24");
        when(packagingRepository.findFirstByProductIdAndIsPurchaseDefaultTrue(5))
                .thenReturn(Optional.of(top));

        assertEquals("Case of 24", service.topLabel(product));

        top.setLabelEn("  ");
        top.setName("Case");
        assertEquals("Case", service.topLabel(product));
    }

    @Test
    void getOrderedPackagingsDelegatesToRepository() {
        when(packagingRepository.findByProductIdOrderBySortOrderAscIdAsc(5))
                .thenReturn(List.of(packaging(5, 1, "Bottle")));

        assertEquals(1, service.getOrderedPackagings(5).size());
    }

    @Test
    void ensureDefaultPackagingsNoOpForNullOrExisting() {
        service.ensureDefaultPackagings(null);

        ProductModel noId = new ProductModel();
        service.ensureDefaultPackagings(noId);

        ProductModel product = product(5, "bottle", "case", 24);
        when(packagingRepository.existsByProductId(5)).thenReturn(true);
        service.ensureDefaultPackagings(product);

        verify(packagingRepository, never()).save(any());
    }

    @Test
    void ensureDefaultPackagingsCreatesBaseOnlyForSingleLevel() {
        ProductModel product = product(5, "bottle", null, 1);
        when(packagingRepository.existsByProductId(5)).thenReturn(false);

        service.ensureDefaultPackagings(product);

        ArgumentCaptor<ProductPackagingModel> saved = ArgumentCaptor.forClass(ProductPackagingModel.class);
        verify(packagingRepository).save(saved.capture());
        assertEquals("base", saved.getValue().getCode());
        assertEquals(1, saved.getValue().getConversionQty());
        assertTrue(Boolean.TRUE.equals(saved.getValue().getIsPurchaseDefault()));
    }

    @Test
    void ensureDefaultPackagingsCreatesBaseAndTop() {
        ProductModel product = product(5, "bottle", "case", 24);
        when(packagingRepository.existsByProductId(5)).thenReturn(false);

        service.ensureDefaultPackagings(product);

        ArgumentCaptor<ProductPackagingModel> saved = ArgumentCaptor.forClass(ProductPackagingModel.class);
        verify(packagingRepository, times(2)).save(saved.capture());
        List<ProductPackagingModel> rows = saved.getAllValues();
        assertEquals("base", rows.get(0).getCode());
        assertFalse(Boolean.TRUE.equals(rows.get(0).getIsPurchaseDefault()));
        assertEquals("top", rows.get(1).getCode());
        assertEquals(24, rows.get(1).getConversionQty());
        assertTrue(Boolean.TRUE.equals(rows.get(1).getIsPurchaseDefault()));
        assertEquals("Case of 24", rows.get(1).getLabelEn());
    }

    @Test
    void fallbackUsesUnitLabelWhenImportUnitBlank() {
        ProductModel product = product(8, "CAN", null, null);
        when(packagingRepository.findFirstByProductIdAndIsPurchaseDefaultTrue(8))
                .thenReturn(Optional.empty());

        ProductPackagingModel result = service.getTopPackaging(product);

        assertEquals("Can", result.getLabelEn());
        assertEquals(1, result.getConversionQty());
    }

    private static ProductModel product(int id, String unit, String importUnit, Integer upu) {
        ProductModel product = new ProductModel();
        product.setId(id);
        product.setUnit(unit);
        product.setImportUnit(importUnit);
        product.setUnitsPerImportUnit(upu);
        return product;
    }

    private static ProductPackagingModel packaging(int productId, int conversion, String label) {
        ProductPackagingModel model = new ProductPackagingModel();
        model.setProductId(productId);
        model.setConversionQty(conversion);
        model.setLabelEn(label);
        model.setName(label);
        model.setIsPurchaseDefault(true);
        return model;
    }
}
