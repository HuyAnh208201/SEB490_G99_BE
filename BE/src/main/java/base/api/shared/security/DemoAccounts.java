package base.api.shared.security;

/**
 * Demo-only accounts that bypass published-shift assignment rules for POS demos.
 */
public final class DemoAccounts {

    public static final String DEMO_CASHIER_EMAIL = "demo_cashier@chainstore.vn";
    public static final String DEMO_IS_EMAIL = "demo_is@chainstore.vn";
    public static final String DEMO_PASSWORD = "123456";
    public static final long DEMO_BRANCH_ID = 1L;

    private DemoAccounts() {
    }

    public static boolean isDemoBypassEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String normalized = email.trim().toLowerCase();
        return DEMO_CASHIER_EMAIL.equals(normalized) || DEMO_IS_EMAIL.equals(normalized);
    }
}
