package base.api.feature.auth.job;

import base.api.feature.auth.repository.IRevokedTokenRepository;
import base.api.feature.auth.service.RevokedTokenCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * A token past its own expiry is already refused by the filter, so the blacklist
 * need not keep it. Both the table and the cache are cleaned so they stay in step.
 */
@Component
public class RevokedTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RevokedTokenCleanupJob.class);

    @Autowired
    private IRevokedTokenRepository revokedTokenRepository;

    @Autowired
    private RevokedTokenCache revokedTokenCache;

    @Scheduled(fixedDelayString = "${auth.revoked-token-cleanup-ms:3600000}")
    @Transactional
    public void purgeExpiredTokens() {
        int remaining = revokedTokenCache.purgeExpired();
        try {
            revokedTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        } catch (DataAccessException ex) {
            // The cache is already clean; a missed database sweep changes no outcome.
            log.warn("Không dọn được token hết hạn trong DB: {}", ex.getMessage());
        }
        log.debug("Blacklist token còn {} entry trong cache", remaining);
    }
}
