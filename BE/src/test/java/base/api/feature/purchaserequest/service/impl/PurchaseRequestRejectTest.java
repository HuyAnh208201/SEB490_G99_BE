package base.api.feature.purchaserequest.service.impl;

import base.api.feature.purchaserequest.dto.request.RejectPurchaseRequestRequest;
import base.api.shared.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Reject is not a product feature. The endpoint/service must refuse every call.
 */
@ExtendWith(MockitoExtension.class)
class PurchaseRequestRejectTest {

    @InjectMocks
    private PurchaseRequestServiceImpl service;

    @Test
    void rejectRequestIsDisabled() {
        RejectPurchaseRequestRequest request = new RejectPurchaseRequestRequest();
        request.setReason("Should not apply");
        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> service.rejectRequest(1L, request));
        assertEquals("Rejecting purchase requests is not supported.", ex.getMessage());
    }
}
