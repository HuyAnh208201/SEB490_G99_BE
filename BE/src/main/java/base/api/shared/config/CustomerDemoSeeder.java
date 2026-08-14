package base.api.shared.config;

import base.api.feature.customer.service.CustomerDemoSeedPolicy;
import base.api.shared.security.DemoAccounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(51)
public class CustomerDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CustomerDemoSeeder.class);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public CustomerDemoSeeder(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Long silver = createIfMissing(
                    DemoAccounts.DEMO_CUSTOMER_SILVER_EMAIL,
                    "0912345678",
                    "Demo Customer Silver",
                    0L,
                    "SILVER");
            createIfMissing(
                    DemoAccounts.DEMO_CUSTOMER_GOLD_EMAIL,
                    "0912345679",
                    "Demo Customer Gold",
                    2500L,
                    "GOLD");
            createIfMissing(
                    DemoAccounts.DEMO_CUSTOMER_PLATINUM_EMAIL,
                    "0912345680",
                    "Demo Customer Platinum",
                    5500L,
                    "PLATINUM");
            if (silver != null) {
                createInvoiceIfMissing(silver);
            }
            forceCustomerRole(DemoAccounts.DEMO_CUSTOMER_SILVER_EMAIL);
            forceCustomerRole(DemoAccounts.DEMO_CUSTOMER_GOLD_EMAIL);
            forceCustomerRole(DemoAccounts.DEMO_CUSTOMER_PLATINUM_EMAIL);
        } catch (Exception exception) {
            log.warn("Customer demo seed skipped without modifying existing rows: {}", exception.getMessage());
        }
    }

    private Long createIfMissing(String email, String phone, String fullName, long points, String tierCode) {
        boolean emailExists = count("SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?)", email) > 0;
        boolean phoneExists = count("SELECT COUNT(*) FROM users WHERE phone = ?", phone) > 0;
        if (!CustomerDemoSeedPolicy.shouldCreate(emailExists, phoneExists)) {
            if (!emailExists) {
                log.warn("Customer demo {} was not created because phone {} is already in use", email, phone);
                return null;
            }
            return jdbc.query("SELECT id FROM users WHERE LOWER(email) = LOWER(?) LIMIT 1",
                    result -> result.next() ? result.getLong(1) : null, email);
        }
        Long roleId = jdbc.query("SELECT id FROM roles WHERE name = 'CUSTOMER' LIMIT 1",
                result -> result.next() ? result.getLong(1) : null);
        Long tierId = jdbc.query("SELECT id FROM membership_tiers WHERE code = ? LIMIT 1",
                result -> result.next() ? result.getLong(1) : null, tierCode);
        if (roleId == null) {
            log.warn("Customer demo seed skipped because CUSTOMER role is missing");
            return null;
        }
        jdbc.update("""
                INSERT INTO users
                  (email, password_hash, full_name, phone, role_id, branch_id, status, points,
                   membership_tier_id, is_verified, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, NULL, 'active', ?, ?, 1, NOW(), NOW())
                """, email, passwordEncoder.encode(DemoAccounts.DEMO_PASSWORD), fullName, phone,
                roleId, points, tierId);
        log.info("Created missing customer demo account: {}", email);
        return jdbc.query("SELECT id FROM users WHERE email = ? LIMIT 1",
                result -> result.next() ? result.getLong(1) : null, email);
    }

    private void forceCustomerRole(String email) {
        int updated = jdbc.update("""
                UPDATE users u
                JOIN roles r ON r.name = 'CUSTOMER'
                SET u.role_id = r.id, u.branch_id = NULL
                WHERE LOWER(u.email) = LOWER(?)
                  AND (r.id <> u.role_id OR u.branch_id IS NOT NULL)
                """, email);
        if (updated > 0) {
            log.info("Kept {} as CUSTOMER (not staff)", email);
        }
    }

    private void createInvoiceIfMissing(Long customerId) {
        String invoiceCode = "DEMO-CS-001";
        boolean exists = count("SELECT COUNT(*) FROM orders WHERE invoice_code = ?", invoiceCode) > 0;
        if (!CustomerDemoSeedPolicy.shouldCreateInvoice(exists)) {
            return;
        }
        Long branchId = jdbc.query("SELECT id FROM branches ORDER BY id LIMIT 1",
                result -> result.next() ? result.getLong(1) : null);
        Long cashierId = jdbc.query("""
                SELECT u.id FROM users u JOIN roles r ON r.id = u.role_id
                WHERE r.name = 'CASHIER' ORDER BY u.id LIMIT 1
                """, result -> result.next() ? result.getLong(1) : null);
        if (branchId == null || cashierId == null) {
            return;
        }
        jdbc.update("""
                INSERT INTO orders
                  (branch_id, shift_id, cashier_id, customer_id, subtotal, discount_amount, total,
                   points_redeemed, points_earned, invoice_code, status, created_at)
                VALUES (?, NULL, ?, ?, 85000, 0, 85000, 0, 8, ?, 'COMPLETED', NOW())
                """, branchId, cashierId, customerId, invoiceCode);
        Long orderId = jdbc.query("SELECT id FROM orders WHERE invoice_code = ? LIMIT 1",
                result -> result.next() ? result.getLong(1) : null, invoiceCode);
        if (orderId != null) {
            jdbc.update("""
                    INSERT INTO point_transactions (customer_id, order_id, points, type, created_at)
                    VALUES (?, ?, 8, 'EARN', NOW())
                    """, customerId, orderId);
        }
        log.info("Created missing customer demo invoice: {}", invoiceCode);
    }

    private int count(String sql, Object value) {
        Integer count = jdbc.queryForObject(sql, Integer.class, value);
        return count == null ? 0 : count;
    }
}
