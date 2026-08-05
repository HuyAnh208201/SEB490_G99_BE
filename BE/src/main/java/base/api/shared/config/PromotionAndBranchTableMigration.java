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
 * Tạo bảng promotion exclusions và branch suspend tokens lúc khởi động.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(0)
public class PromotionAndBranchTableMigration {

    private static final Logger log = LoggerFactory.getLogger(PromotionAndBranchTableMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS campaign_branch_exclusions (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        campaign_id BIGINT NOT NULL,
                        branch_id BIGINT NOT NULL,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_campaign_branch_exclusion (campaign_id, branch_id)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS branch_suspend_tokens (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        branch_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        verification_code VARCHAR(6) NOT NULL,
                        expires_at DATETIME NOT NULL,
                        used TINYINT(1) NOT NULL DEFAULT 0,
                        created_at DATETIME NOT NULL,
                        PRIMARY KEY (id),
                        KEY idx_branch_suspend_branch_user (branch_id, user_id)
                    )
                    """);
            log.info("Ensured campaign_branch_exclusions / branch_suspend_tokens tables exist");
        } catch (Exception ex) {
            log.warn("promotion/branch table migration skipped: {}", ex.getMessage());
        }
    }
}
