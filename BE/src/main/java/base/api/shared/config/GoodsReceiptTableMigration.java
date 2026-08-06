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
 * Tạo bảng nhận hàng lúc khởi động (CREATE TABLE IF NOT EXISTS - an toàn nếu DBA đã tạo sẵn).
 */
@Component
@ConditionalOnStartupBootstrap
@Order(0)
public class GoodsReceiptTableMigration {

    private static final Logger log = LoggerFactory.getLogger(GoodsReceiptTableMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS goods_receipts (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        purchase_request_id BIGINT NULL,
                        branch_id BIGINT NOT NULL,
                        stock_staff_id BIGINT NOT NULL,
                        status VARCHAR(255) NULL,
                        received_at DATETIME NULL,
                        PRIMARY KEY (id)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS goods_receipt_items (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        goods_receipt_id BIGINT NOT NULL,
                        product_id INT NOT NULL,
                        ordered_quantity INT NULL,
                        received_quantity INT NULL,
                        PRIMARY KEY (id)
                    )
                    """);
            log.info("Ensured goods_receipts / goods_receipt_items tables exist");
        } catch (Exception ex) {
            log.warn("goods_receipts migration skipped: {}", ex.getMessage());
        }
    }
}
