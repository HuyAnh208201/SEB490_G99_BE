package base.api.feature.posscan.service.impl;

import base.api.feature.posscan.dto.request.PushScanEventRequest;
import base.api.feature.posscan.dto.response.ScanEventFeedResponse;
import base.api.feature.posscan.repository.PosScanEventRepository;
import base.api.feature.product.dto.response.ProductResponse;
import base.api.feature.product.service.IProductService;
import base.api.shared.entity.PosScanEventModel;
import base.api.shared.entity.UserModel;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PosScanServiceImpl} push / poll scan event flow.
 */
@ExtendWith(MockitoExtension.class)
class PosScanServiceImplTest {

    @Mock private PosScanEventRepository scanEventRepository;
    @Mock private IProductService productService;
    @Mock private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private PosScanServiceImpl service;

    @Test
    void pushScanEventPersistsValidatedBarcode() {
        asCashier();
        ProductResponse product = new ProductResponse();
        product.setId(10);
        product.setName("Cola");
        when(productService.scanByBarcode("8901234567890")).thenReturn(product);
        when(scanEventRepository.save(any(PosScanEventModel.class))).thenAnswer(inv -> inv.getArgument(0));

        PushScanEventRequest request = new PushScanEventRequest();
        request.setBarcode("8901234567890");

        ProductResponse response = service.pushScanEvent(request);

        assertEquals(10, response.getId());
        ArgumentCaptor<PosScanEventModel> captor = ArgumentCaptor.forClass(PosScanEventModel.class);
        verify(scanEventRepository).save(captor.capture());
        assertEquals(3L, captor.getValue().getCashierUserId());
        assertEquals(5L, captor.getValue().getBranchId());
        assertEquals("8901234567890", captor.getValue().getBarcode());
        assertEquals(10, captor.getValue().getProductId());
        assertNull(captor.getValue().getErrorMessage());
    }

    @Test
    void pushScanEventPersistsErrorWhenBarcodeInvalid() {
        asCashier();
        when(productService.scanByBarcode("BAD")).thenThrow(new NotFoundException("Product not found for this barcode."));
        when(scanEventRepository.save(any(PosScanEventModel.class))).thenAnswer(inv -> inv.getArgument(0));

        PushScanEventRequest request = new PushScanEventRequest();
        request.setBarcode("BAD");

        NotFoundException error = assertThrows(NotFoundException.class, () -> service.pushScanEvent(request));

        assertEquals("Product not found for this barcode.", error.getMessage());
        ArgumentCaptor<PosScanEventModel> captor = ArgumentCaptor.forClass(PosScanEventModel.class);
        verify(scanEventRepository).save(captor.capture());
        assertEquals("BAD", captor.getValue().getBarcode());
        assertNull(captor.getValue().getProductId());
        assertEquals("Product not found for this barcode.", captor.getValue().getErrorMessage());
    }

    @Test
    void pushScanEventPersistsOutOfStockErrorThenRethrows() {
        asCashier();
        when(productService.scanByBarcode("8901234567890"))
                .thenThrow(new BadRequestException("This product is out of stock at your branch."));
        when(scanEventRepository.save(any(PosScanEventModel.class))).thenAnswer(inv -> inv.getArgument(0));

        PushScanEventRequest request = new PushScanEventRequest();
        request.setBarcode("8901234567890");

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.pushScanEvent(request));

        assertTrue(error.getMessage().contains("out of stock"));
        ArgumentCaptor<PosScanEventModel> captor = ArgumentCaptor.forClass(PosScanEventModel.class);
        verify(scanEventRepository).save(captor.capture());
        assertNull(captor.getValue().getProductId());
        assertEquals("This product is out of stock at your branch.", captor.getValue().getErrorMessage());
    }

    @Test
    void pollScanEventsWithNullAfterIdReturnsCursorOnly() {
        asCashier();
        when(scanEventRepository.findLatestIdByCashierUserId(3L)).thenReturn(42L);

        ScanEventFeedResponse response = service.pollScanEvents(null);

        assertEquals(42L, response.getLatestId());
        assertTrue(response.getEvents().isEmpty());
        verify(scanEventRepository, never())
                .findByCashierUserIdAndIdGreaterThanAndCreatedAtAfterOrderByIdAsc(anyLong(), anyLong(), any());
    }

    @Test
    void pollScanEventsReturnsEventsAfterCursor() {
        asCashier();
        when(scanEventRepository.findLatestIdByCashierUserId(3L)).thenReturn(12L);

        PosScanEventModel event = new PosScanEventModel();
        event.setId(11L);
        event.setBarcode("8901234567890");
        event.setProductId(10);
        event.setProductName("Cola");
        event.setCreatedAt(LocalDateTime.now());
        when(scanEventRepository.findByCashierUserIdAndIdGreaterThanAndCreatedAtAfterOrderByIdAsc(
                eq(3L), eq(10L), any(LocalDateTime.class)))
                .thenReturn(List.of(event));

        ScanEventFeedResponse response = service.pollScanEvents(10L);

        assertEquals(11L, response.getLatestId());
        assertEquals(1, response.getEvents().size());
        assertEquals("8901234567890", response.getEvents().get(0).getBarcode());
        assertTrue(response.getEvents().get(0).isSuccess());
        assertNull(response.getEvents().get(0).getErrorMessage());
    }

    @Test
    void pollScanEventsReturnsErrorEvents() {
        asCashier();
        when(scanEventRepository.findLatestIdByCashierUserId(3L)).thenReturn(15L);

        PosScanEventModel event = new PosScanEventModel();
        event.setId(14L);
        event.setBarcode("8901234567890");
        event.setProductId(null);
        event.setProductName(null);
        event.setErrorMessage("This product is out of stock at your branch.");
        event.setCreatedAt(LocalDateTime.now());
        when(scanEventRepository.findByCashierUserIdAndIdGreaterThanAndCreatedAtAfterOrderByIdAsc(
                eq(3L), eq(10L), any(LocalDateTime.class)))
                .thenReturn(List.of(event));

        ScanEventFeedResponse response = service.pollScanEvents(10L);

        assertEquals(1, response.getEvents().size());
        assertFalse(response.getEvents().get(0).isSuccess());
        assertEquals("This product is out of stock at your branch.", response.getEvents().get(0).getErrorMessage());
        assertNull(response.getEvents().get(0).getProductId());
    }

    @Test
    void pollScanEventsAdvancesCursorWhenNoFreshEvents() {
        asCashier();
        when(scanEventRepository.findLatestIdByCashierUserId(3L)).thenReturn(20L);
        when(scanEventRepository.findByCashierUserIdAndIdGreaterThanAndCreatedAtAfterOrderByIdAsc(
                eq(3L), eq(5L), any(LocalDateTime.class)))
                .thenReturn(List.of());

        ScanEventFeedResponse response = service.pollScanEvents(5L);

        assertEquals(20L, response.getLatestId());
        assertTrue(response.getEvents().isEmpty());
    }

    private void asCashier() {
        UserModel cashier = new UserModel();
        cashier.setId(3L);
        cashier.setBranchId(5L);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
    }
}
