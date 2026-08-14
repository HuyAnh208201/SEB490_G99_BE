package base.api.feature.customer.service;

import base.api.feature.customer.dto.CustomerAuthDtos;
import base.api.feature.customer.util.CustomerEmailNormalizer;
import base.api.feature.customer.util.CustomerPhoneNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerNormalizerTest {

    @Test
    void normalizesMobileEmailPhoneAndLegacyLoginAlias() {
        assertEquals("customer@example.com", CustomerEmailNormalizer.normalize(" Customer@Example.COM "));
        assertTrue(CustomerEmailNormalizer.isValid("customer@example.com"));
        assertFalse(CustomerEmailNormalizer.isValid("0912@customer.chainstore.local"));
        assertEquals("0912345678", CustomerPhoneNormalizer.normalize("+84 912 345 678"));
        assertEquals("0912345678", CustomerPhoneNormalizer.normalize("84912345678"));
        assertTrue(CustomerPhoneNormalizer.isValidVnMobile("0912345678"));
        assertFalse(CustomerPhoneNormalizer.isValidVnMobile("123"));
        assertNull(CustomerPhoneNormalizer.normalize(null));

        CustomerAuthDtos.LoginRequest request = new CustomerAuthDtos.LoginRequest();
        request.setPhone(" 0912345678 ");
        assertEquals("0912345678", request.resolvedIdentifier());
        request.setIdentifier(" customer@example.com ");
        assertEquals("customer@example.com", request.resolvedIdentifier());
    }
}
