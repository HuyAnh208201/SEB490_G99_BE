package base.api.shared.security;

import java.util.Locale;
import java.util.Set;

/**
 * Canonical demo accounts kept on the shared DB.
 * Cashier bypasses published-shift assignment; IS bypasses inventory-count hours on the FE.
 */
public final class DemoAccounts {

    public static final String DEMO_CASHIER_EMAIL = "demo_cashier@chainstore.vn";
    public static final String DEMO_IS_EMAIL = "demo_is@chainstore.vn";
    public static final String Q1_CASHIER_2_EMAIL = "cashier.q1.2@chainstore.vn";
    public static final String Q1_CASHIER_2_NAME = "Lê Hoàng Ngân Q1";
    public static final String Q1_CASHIER_2_PHONE = "0900000018";
    public static final String DEMO_CUSTOMER_SILVER_EMAIL = "demo.customer.silver@chainstore.vn";
    public static final String DEMO_CUSTOMER_GOLD_EMAIL = "demo.customer.gold@chainstore.vn";
    public static final String DEMO_CUSTOMER_PLATINUM_EMAIL = "demo.customer.platinum@chainstore.vn";
    public static final String DEMO_PASSWORD = "123456";
    public static final long DEMO_BRANCH_ID = 1L;

    /** Legacy emails soft-locked on every boot. */
    public static final String LEGACY_POS_DEMO_CASHIER = "pos_demo_cashier@chainstore.vn";
    public static final String LEGACY_POS_DEMO_BM = "pos_demo_bm@chainstore.vn";
    public static final String LEGACY_DEMO_CUSTOMER = "demo.customer@chainstore.vn";

    private static final Set<String> WHITELIST = Set.of(
            DEMO_CASHIER_EMAIL,
            DEMO_IS_EMAIL,
            DEMO_CUSTOMER_SILVER_EMAIL,
            DEMO_CUSTOMER_GOLD_EMAIL,
            DEMO_CUSTOMER_PLATINUM_EMAIL
    );

    private DemoAccounts() {
    }

    public static String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** POS shift-assignment bypass — cashier only. */
    public static boolean isDemoCashierBypassEmail(String email) {
        return DEMO_CASHIER_EMAIL.equals(normalizeEmail(email));
    }

    public static boolean isDemoIsEmail(String email) {
        return DEMO_IS_EMAIL.equals(normalizeEmail(email));
    }

    /** @deprecated use {@link #isDemoCashierBypassEmail(String)} */
    @Deprecated
    public static boolean isDemoBypassEmail(String email) {
        return isDemoCashierBypassEmail(email);
    }

    public static boolean isWhitelistedDemoEmail(String email) {
        return WHITELIST.contains(normalizeEmail(email));
    }

    public static Set<String> whitelist() {
        return WHITELIST;
    }
}
