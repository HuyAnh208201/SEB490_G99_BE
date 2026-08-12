package base.api.feature.auth.service;

import base.api.feature.auth.repository.IRevokedTokenRepository;
import base.api.shared.entity.RevokedTokenModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory mirror of the revoked-token list, so the filter never queries the database.
 *
 * Why: MySQL runs on another host, so every lookup is a network round trip of roughly
 * 56ms. An A/B measurement showed the per-request lookup added about 11% latency to
 * every authenticated request. Reading memory brings that back to zero.
 *
 * The database stays the source of truth: the cache is reloaded in full at startup,
 * so a restart cannot bring a revoked token back to life.
 *
 * LIMIT — multiple instances: each keeps its own copy, so instance A does not see a
 * token instance B just revoked until A restarts. The project runs a single instance
 * today, so this costs nothing yet. Scaling out means moving to a negative cache with
 * a short TTL, which bounds the staleness, or to a shared Redis.
 */
@Component
public class RevokedTokenCache {

    private static final Logger log = LoggerFactory.getLogger(RevokedTokenCache.class);

    @Autowired
    private IRevokedTokenRepository revokedTokenRepository;

    /** token_hash -> the moment the token expires on its own. */
    private final Map<String, LocalDateTime> revoked = new ConcurrentHashMap<>();

    /**
     * Loaded once the context is ready rather than from @PostConstruct: reading is only
     * meaningful after RevokedTokenTableMigration has created the table.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try {
            LocalDateTime now = LocalDateTime.now();
            for (RevokedTokenModel token : revokedTokenRepository.findAll()) {
                if (token.getExpiresAt() != null && token.getExpiresAt().isAfter(now)) {
                    revoked.put(token.getTokenHash(), token.getExpiresAt());
                }
            }
            log.info("Nạp {} token đã thu hồi còn hiệu lực vào cache", revoked.size());
        } catch (DataAccessException ex) {
            // Fail-open for the same reason as JwtAuthenticationFilter: throwing here stops the
            // whole application from starting, while the cost is only losing revocation for now.
            log.error("Không nạp được blacklist token, tính năng thu hồi tạm ngưng. "
                    + "Kiểm tra bảng revoked_tokens: {}", ex.getMessage());
        }
    }

    public boolean isRevoked(String tokenHash) {
        LocalDateTime expiresAt = revoked.get(tokenHash);
        if (expiresAt == null) {
            return false;
        }
        // A token past its own expiry is refused by the filter anyway — drop it to save room.
        if (expiresAt.isBefore(LocalDateTime.now())) {
            revoked.remove(tokenHash);
            return false;
        }
        return true;
    }

    public void remember(String tokenHash, LocalDateTime expiresAt) {
        revoked.put(tokenHash, expiresAt);
    }

    /** Drops expired entries; returns how many remain. */
    public int purgeExpired() {
        LocalDateTime now = LocalDateTime.now();
        revoked.values().removeIf(expiresAt -> expiresAt.isBefore(now));
        return revoked.size();
    }

    public int size() {
        return revoked.size();
    }
}
