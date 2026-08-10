package base.api.shared.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Buy X get Y is retired — deactivate any remaining rows so they cannot stay ACTIVE.
 */
@Component
@Order(55)
public class DeactivateBuyXGetYCampaignsMigration {

    private static final Logger log = LoggerFactory.getLogger(DeactivateBuyXGetYCampaignsMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void deactivateBuyXGetY() {
        try {
            int changed = jdbcTemplate.update(
                    """
                    UPDATE campaigns
                    SET status = 'DEACTIVATED'
                    WHERE type = 'BUY_X_GET_Y'
                      AND status <> 'DEACTIVATED'
                    """);
            if (changed > 0) {
                log.info("Deactivated {} Buy X get Y campaign(s)", changed);
            }
        } catch (Exception ex) {
            log.warn("Buy X get Y deactivation skipped: {}", ex.getMessage());
        }
    }
}
