package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * DB cũ dùng ENUM role không có DIRECTOR / BRANCH_MANAGER / WAREHOUSE_MANAGER.
 * Chuyển sang VARCHAR để JPA EnumType.STRING hoạt động với mọi role.
 */
@Component
@ConditionalOnStartupBootstrap
@Order(0)
public class RoleColumnMigration {

    private static final Logger log = LoggerFactory.getLogger(RoleColumnMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrateRoleColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE user MODIFY COLUMN role VARCHAR(50) NULL");
            log.info("Migrated user.role column to VARCHAR(50)");
        } catch (Exception ex) {
            log.warn("user.role migration skipped: {}", ex.getMessage());
        }
    }
}
