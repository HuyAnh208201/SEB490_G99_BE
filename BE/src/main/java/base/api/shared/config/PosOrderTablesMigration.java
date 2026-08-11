package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * DB dùng ddl-auto=none nên @Entity mới không tự tạo bảng.
 *
 * Bốn bảng bán hàng tại quầy. Lưu ý {@code orders.customer_id} trỏ tới
 * {@code users.id} chứ KHÔNG phải bảng {@code customers} trong bản thiết kế gốc:
 * cả hệ thống coi khách hàng là user role CUSTOMER với cột points, dùng bảng
 * customers sẽ tách dữ liệu tích điểm ra hai nơi. Vì vậy không đặt FK.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(0)
public class PosOrderTablesMigration {

    private static final Logger log = LoggerFactory.getLogger(PosOrderTablesMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS orders (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        branch_id BIGINT NOT NULL,
                        shift_id BIGINT NULL,
                        cashier_id BIGINT NOT NULL,
                        customer_id BIGINT NULL,
                        subtotal DECIMAL(15,2) NOT NULL DEFAULT 0,
                        discount_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
                        total DECIMAL(15,2) NOT NULL DEFAULT 0,
                        points_redeemed BIGINT NOT NULL DEFAULT 0,
                        points_earned BIGINT NOT NULL DEFAULT 0,
                        invoice_code VARCHAR(64) NULL,
                        status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
                        created_at DATETIME NOT NULL,
                        PRIMARY KEY (id),
                        UNIQUE KEY uq_orders_invoice_code (invoice_code),
                        KEY idx_orders_branch_created (branch_id, created_at),
                        KEY idx_orders_shift (shift_id)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS order_items (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        order_id BIGINT NOT NULL,
                        product_id INT NOT NULL,
                        product_name VARCHAR(255) NULL,
                        quantity INT NOT NULL,
                        unit_price DECIMAL(15,2) NOT NULL,
                        line_total DECIMAL(15,2) NOT NULL,
                        PRIMARY KEY (id),
                        KEY idx_order_items_order (order_id)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS order_discounts (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        order_id BIGINT NOT NULL,
                        voucher_id BIGINT NULL,
                        code VARCHAR(64) NULL,
                        discount_amount DECIMAL(15,2) NOT NULL,
                        PRIMARY KEY (id),
                        KEY idx_order_discounts_order (order_id)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS payments (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        order_id BIGINT NOT NULL,
                        method VARCHAR(32) NOT NULL,
                        amount DECIMAL(15,2) NOT NULL,
                        cash_received DECIMAL(15,2) NULL,
                        change_amount DECIMAL(15,2) NULL,
                        transaction_ref VARCHAR(255) NULL,
                        status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
                        created_at DATETIME NOT NULL,
                        PRIMARY KEY (id),
                        KEY idx_payments_order (order_id)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS voucher_catalog (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        name VARCHAR(255) NOT NULL,
                        discount_type VARCHAR(32) NOT NULL,
                        discount_value DECIMAL(15,2) NOT NULL,
                        points_required INT NOT NULL DEFAULT 0,
                        status VARCHAR(32) NOT NULL DEFAULT 'active',
                        PRIMARY KEY (id)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS vouchers (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        code VARCHAR(64) NOT NULL,
                        voucher_catalog_id BIGINT NULL,
                        customer_id BIGINT NULL,
                        status VARCHAR(32) NOT NULL DEFAULT 'active',
                        expires_at DATETIME NULL,
                        created_at DATETIME NULL,
                        PRIMARY KEY (id),
                        UNIQUE KEY uq_vouchers_code (code)
                    )
                    """);
            // Bảng orders/order_items/order_discounts có thể đã tồn tại từ bản thiết kế
            // gốc mà thiếu các cột bổ sung. CREATE TABLE IF NOT EXISTS ở trên bỏ qua bảng
            // đã có, nên phải thêm cột lẻ tại đây.
            addColumnIfMissing("orders", "invoice_code", "VARCHAR(64) NULL");
            addColumnIfMissing("orders", "points_redeemed", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing("orders", "points_earned", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing("order_items", "product_name", "VARCHAR(255) NULL");
            addColumnIfMissing("order_discounts", "code", "VARCHAR(64) NULL");

            // Bảng orders gốc đặt FK customer_id -> customers(id), nhưng cả hệ thống coi
            // khách là user role CUSTOMER với điểm trên users.points (CashierServiceImpl,
            // getOrCreateGuestByPhone). customer_id thực chất trỏ users.id, nên FK sang
            // customers phải gỡ, nếu không mọi đơn có khách đều vi phạm ràng buộc.
            dropForeignKeyIfPresent("orders", "customer_id", "customers");

            // vouchers.customer_id mắc đúng lỗi đó: schema gốc trỏ customers(id) trong khi
            // checkout so nó với users.id, nên mã phát riêng cho khách sẽ khớp nhầm người.
            // Ánh xạ lại đúng một lần — chỉ khi FK còn đó, tức DB chưa từng được chuyển.
            if (dropForeignKeyIfPresent("vouchers", "customer_id", "customers")) {
                remapVoucherCustomerIds();
            }

            log.info("Ensured POS order tables exist");
        } catch (Exception ex) {
            log.warn("POS order tables migration skipped: {}", ex.getMessage());
        }
    }

    /**
     * Chỉ chạy đúng một lần, ngay sau khi gỡ FK — mốc "DB còn ở schema gốc". Vì thế
     * hỏng ở đây là hỏng vĩnh viễn: mã sẽ khớp nhầm một users.id không liên quan.
     * Log ERROR kèm việc cần làm thay vì để lẫn vào cảnh báo chung của migration.
     */
    private void remapVoucherCustomerIds() {
        try {
            Integer orphans = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*) FROM vouchers v
                            JOIN customers c ON v.customer_id = c.id
                            WHERE c.user_id IS NULL
                            """,
                    Integer.class);
            int remapped = jdbcTemplate.update(
                    """
                            UPDATE vouchers v
                            JOIN customers c ON v.customer_id = c.id
                            SET v.customer_id = c.user_id
                            WHERE c.user_id IS NOT NULL
                            """);
            if (remapped > 0) {
                log.info("Re-pointed {} voucher(s) from customers.id to users.id", remapped);
            }
            if (orphans != null && orphans > 0) {
                log.error(
                        "{} voucher(s) point at customers rows without user_id and were left "
                                + "in the old ID space. They will match the wrong customer at "
                                + "checkout — fix vouchers.customer_id for these rows by hand.",
                        orphans);
            }
        } catch (Exception ex) {
            log.error(
                    "Failed to re-point vouchers.customer_id from customers.id to users.id. "
                            + "The FK is already dropped so this will NOT be retried: run the "
                            + "UPDATE manually or customer-specific codes will match the wrong "
                            + "customer. Cause: {}",
                    ex.getMessage(), ex);
        }
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        Integer exists = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                          AND COLUMN_NAME = ?
                        """,
                Integer.class,
                table,
                column);
        if (exists == null || exists == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            log.info("Added column {}.{}", table, column);
        }
    }

    /**
     * Tên FK do MySQL tự sinh nên tra theo cột/bảng đích thay vì đoán tên.
     * Trả về true khi vừa gỡ được FK — dùng làm mốc "DB còn ở schema gốc" cho các
     * bước chuyển dữ liệu chỉ được chạy một lần.
     */
    private boolean dropForeignKeyIfPresent(String table, String column, String referencedTable) {
        String constraintName = jdbcTemplate.query(
                """
                        SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                          AND COLUMN_NAME = ?
                          AND REFERENCED_TABLE_NAME = ?
                        LIMIT 1
                        """,
                rs -> rs.next() ? rs.getString(1) : null,
                table,
                column,
                referencedTable);
        if (constraintName == null) {
            return false;
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " DROP FOREIGN KEY " + constraintName);
        log.info("Dropped FK {} on {}.{}", constraintName, table, column);
        return true;
    }
}
