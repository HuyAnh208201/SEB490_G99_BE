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
 * Token đã hết hạn tự nhiên thì filter chặn sẵn, không cần giữ trong blacklist.
 * Dọn cả DB lẫn cache để hai bên không lệch nhau.
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
            // Cache đã dọn xong; DB dọn hụt một vòng cũng không sai kết quả.
            log.warn("Không dọn được token hết hạn trong DB: {}", ex.getMessage());
        }
        log.debug("Blacklist token còn {} entry trong cache", remaining);
    }
}
