package base.api.feature.customer.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerDemoSeedPolicyTest {

    @Test
    void createsAccountOnlyWhenNeitherEmailNorPhoneExists() {
        assertTrue(CustomerDemoSeedPolicy.shouldCreate(false, false));
        assertFalse(CustomerDemoSeedPolicy.shouldCreate(true, false));
        assertFalse(CustomerDemoSeedPolicy.shouldCreate(false, true));
        assertFalse(CustomerDemoSeedPolicy.shouldCreate(true, true));
    }

    @Test
    void createsInvoiceOnlyWhenInvoiceCodeDoesNotExist() {
        assertTrue(CustomerDemoSeedPolicy.shouldCreateInvoice(false));
        assertFalse(CustomerDemoSeedPolicy.shouldCreateInvoice(true));
    }
}
