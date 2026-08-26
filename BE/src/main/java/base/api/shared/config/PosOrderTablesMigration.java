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
                        refundable TINYINT(1) NOT NULL DEFAULT 1,
                        PRIMARY KEY (id),
                        KEY idx_order_items_order (order_id)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS order_discounts (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        order_id BIGINT NOT NULL,
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
            // Bảng orders/order_items/order_discounts có thể đã tồn tại từ bản thiết kế
            // gốc mà thiếu các cột bổ sung. CREATE TABLE IF NOT EXISTS ở trên bỏ qua bảng
            // đã có, nên phải thêm cột lẻ tại đây.
            addColumnIfMissing("orders", "invoice_code", "VARCHAR(64) NULL");
            addColumnIfMissing("orders", "points_redeemed", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing("orders", "points_earned", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing("order_items", "product_name", "VARCHAR(255) NULL");
            addColumnIfMissing("order_items", "refundable", "TINYINT(1) NOT NULL DEFAULT 1");
            addColumnIfMissing("order_discounts", "code", "VARCHAR(64) NULL");

            // Bảng orders gốc đặt FK customer_id -> customers(id), nhưng cả hệ thống coi
            // khách là user role CUSTOMER với điểm trên users.points (CashierServiceImpl,
            // getOrCreateGuestByPhone). customer_id thực chất trỏ users.id, nên FK sang
            // customers phải gỡ, nếu không mọi đơn có khách đều vi phạm ràng buộc.
            dropForeignKeyIfPresent("orders", "customer_id", "customers");

            log.info("Ensured POS order tables exist");
        } catch (Exception ex) {
            log.warn("POS order tables migration skipped: {}", ex.getMessage());
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

    /** Tên FK do MySQL tự sinh nên tra theo cột/bảng đích thay vì đoán tên. */
    private void dropForeignKeyIfPresent(String table, String column, String referencedTable) {
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
        if (constraintName != null) {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP FOREIGN KEY " + constraintName);
            log.info("Dropped FK {} on {}.{}", constraintName, table, column);
        }
    }
}
