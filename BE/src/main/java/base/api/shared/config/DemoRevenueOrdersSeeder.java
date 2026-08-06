package base.api.shared.config;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Seeds ~700 COMPLETED demo orders across branches for Revenue Dashboard testing.
 * Idempotent: skips when {@code DEMO-REV-%} invoice count is already &gt;= TARGET.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(55)
public class DemoRevenueOrdersSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRevenueOrdersSeeder.class);
    private static final int TARGET = 700;
    private static final String INVOICE_PREFIX = "DEMO-REV-";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IBranchRepository branchRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IProductRepository productRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            Integer existing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM orders WHERE invoice_code LIKE ?",
                    Integer.class,
                    INVOICE_PREFIX + "%");
            int have = existing == null ? 0 : existing;
            if (have >= TARGET) {
                log.info("Demo revenue orders already present ({} >= {}). Skipping.", have, TARGET);
                return;
            }

            List<BranchModel> branches = branchRepository.findAll().stream()
                    .filter(b -> b.getId() != null)
                    .toList();
            if (branches.isEmpty()) {
                log.warn("Demo revenue seed skipped: no branches.");
                return;
            }

            List<ProductModel> products = productRepository.findAllActiveProducts();
            if (products.isEmpty()) {
                products = productRepository.findAll();
            }
            if (products.isEmpty()) {
                log.warn("Demo revenue seed skipped: no products.");
                return;
            }

            Map<Long, List<Long>> cashiersByBranch = new HashMap<>();
            for (BranchModel branch : branches) {
                List<Long> cashiers = userRepository
                        .findByBranchIdAndRoleName(branch.getId(), UserRole.CASHIER.name())
                        .stream()
                        .map(UserModel::getId)
                        .filter(Objects::nonNull)
                        .toList();
                if (!cashiers.isEmpty()) {
                    cashiersByBranch.put(branch.getId(), cashiers);
                }
            }
            if (cashiersByBranch.isEmpty()) {
                // Fallback: any cashier in the system
                List<UserModel> anyCashiers = userRepository.findAll().stream()
                        .filter(u -> u.getRole() == UserRole.CASHIER && u.getBranchId() != null)
                        .toList();
                for (UserModel c : anyCashiers) {
                    cashiersByBranch
                            .computeIfAbsent(c.getBranchId(), ignored -> new ArrayList<>())
                            .add(c.getId());
                }
            }
            if (cashiersByBranch.isEmpty()) {
                log.warn("Demo revenue seed skipped: no cashiers.");
                return;
            }

            List<BranchModel> seededBranches = branches.stream()
                    .filter(b -> cashiersByBranch.containsKey(b.getId()))
                    .toList();
            if (seededBranches.isEmpty()) {
                log.warn("Demo revenue seed skipped: no branch with cashiers.");
                return;
            }

            int toCreate = TARGET - have;
            Random random = ThreadLocalRandom.current();
            LocalDateTime now = LocalDateTime.now();
            int created = 0;

            for (int i = 0; i < toCreate; i++) {
                BranchModel branch = seededBranches.get(random.nextInt(seededBranches.size()));
                List<Long> cashiers = cashiersByBranch.get(branch.getId());
                Long cashierId = cashiers.get(random.nextInt(cashiers.size()));

                int dayOffset = random.nextInt(28);
                int hour = 7 + random.nextInt(14);
                int minute = random.nextInt(60);
                LocalDateTime createdAt = now.minusDays(dayOffset).withHour(hour).withMinute(minute).withSecond(0).withNano(0);

                int lineCount = 1 + random.nextInt(4);
                List<LineDraft> lines = new ArrayList<>(lineCount);
                BigDecimal subtotal = BigDecimal.ZERO;
                for (int L = 0; L < lineCount; L++) {
                    ProductModel product = products.get(random.nextInt(products.size()));
                    BigDecimal unit = resolveUnitPrice(product);
                    int qty = 1 + random.nextInt(5);
                    BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
                    lines.add(new LineDraft(product.getId(), product.getName(), qty, unit, lineTotal));
                    subtotal = subtotal.add(lineTotal);
                }
                subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

                String provisionalCode = INVOICE_PREFIX + "TMP-" + System.nanoTime() + "-" + i;
                Long orderId = insertOrder(branch.getId(), cashierId, subtotal, provisionalCode, createdAt);
                String invoiceCode = INVOICE_PREFIX + String.format("%06d", orderId);
                jdbcTemplate.update("UPDATE orders SET invoice_code = ? WHERE id = ?", invoiceCode, orderId);

                for (LineDraft line : lines) {
                    insertItem(orderId, line);
                }
                insertPayment(orderId, subtotal, createdAt);
                created += 1;
            }

            log.info("Demo revenue orders seeded: +{} (target {}).", created, TARGET);
        } catch (Exception ex) {
            log.warn("Demo revenue order seed skipped: {}", ex.getMessage());
        }
    }

    private BigDecimal resolveUnitPrice(ProductModel product) {
        BigDecimal price = product.getDefaultSalePrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            price = product.getReferenceImportPrice();
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.valueOf(15_000 + ThreadLocalRandom.current().nextInt(85_000))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private Long insertOrder(
            Long branchId, Long cashierId, BigDecimal total, String invoiceCode, LocalDateTime createdAt) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                            INSERT INTO orders
                              (branch_id, shift_id, cashier_id, customer_id, subtotal, discount_amount, total,
                               points_redeemed, points_earned, invoice_code, status, created_at)
                            VALUES (?, NULL, ?, NULL, ?, 0, ?, 0, 0, ?, 'COMPLETED', ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, branchId);
            ps.setLong(2, cashierId);
            ps.setBigDecimal(3, total);
            ps.setBigDecimal(4, total);
            ps.setString(5, invoiceCode);
            ps.setTimestamp(6, Timestamp.valueOf(createdAt));
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to insert demo order.");
        }
        return key.longValue();
    }

    private void insertItem(Long orderId, LineDraft line) {
        jdbcTemplate.update(
                """
                        INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, line_total)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                orderId,
                line.productId(),
                line.productName() == null ? "Product" : line.productName(),
                line.qty(),
                line.unitPrice(),
                line.lineTotal());
    }

    private void insertPayment(Long orderId, BigDecimal amount, LocalDateTime createdAt) {
        jdbcTemplate.update(
                """
                        INSERT INTO payments
                          (order_id, method, amount, cash_received, change_amount, transaction_ref, status, created_at)
                        VALUES (?, 'CASH', ?, ?, 0, NULL, 'SUCCESS', ?)
                        """,
                orderId,
                amount,
                amount,
                Timestamp.valueOf(createdAt));
    }

    private record LineDraft(
            Integer productId, String productName, int qty, BigDecimal unitPrice, BigDecimal lineTotal) {
    }
}
