package base.api.feature.customer.service;

public final class CustomerDemoSeedPolicy {

    private CustomerDemoSeedPolicy() {
    }

    public static boolean shouldCreate(boolean emailExists, boolean phoneExists) {
        return !emailExists && !phoneExists;
    }

    public static boolean shouldCreateInvoice(boolean invoiceCodeExists) {
        return !invoiceCodeExists;
    }
}
