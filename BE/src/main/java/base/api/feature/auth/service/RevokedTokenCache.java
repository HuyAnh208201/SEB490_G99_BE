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
 * Bản sao trong RAM của blacklist token, để filter khỏi phải query DB mỗi request.
 *
 * Vì sao cần: MySQL nằm ở máy khác (72.61.114.184), nên mỗi query là một round-trip
 * mạng ~56ms. Đo A/B với code cũ cho thấy query blacklist làm mọi request có token
 * chậm thêm 11%. Tra RAM đưa con số đó về 0.
 *
 * DB vẫn là nguồn chân lý: cache được nạp lại đầy đủ lúc khởi động, nên restart
 * không làm token đã thu hồi sống lại.
 *
 * GIỚI HẠN — chạy nhiều instance: mỗi instance giữ cache riêng, nên instance A
 * không thấy token mà instance B vừa thu hồi cho tới lần khởi động sau. Hiện dự án
 * chạy một instance nên chưa ảnh hưởng. Khi nào scale ngang thì đổi sang cache phủ
 * định có TTL ngắn (staleness bị chặn trên bằng TTL) hoặc dùng Redis dùng chung.
 */
@Component
public class RevokedTokenCache {

    private static final Logger log = LoggerFactory.getLogger(RevokedTokenCache.class);

    @Autowired
    private IRevokedTokenRepository revokedTokenRepository;

    /** token_hash -> thời điểm token hết hạn tự nhiên. */
    private final Map<String, LocalDateTime> revoked = new ConcurrentHashMap<>();

    /**
     * Nạp sau khi context sẵn sàng chứ không dùng @PostConstruct: cần
     * RevokedTokenTableMigration tạo xong bảng thì đọc mới có nghĩa.
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
            // Cùng lý do fail-open như JwtAuthenticationFilter: chết ở đây thì cả app
            // không khởi động được, trong khi hậu quả chỉ là tạm mất khả năng thu hồi.
            log.error("Không nạp được blacklist token, tính năng thu hồi tạm ngưng. "
                    + "Kiểm tra bảng revoked_tokens: {}", ex.getMessage());
        }
    }

    public boolean isRevoked(String tokenHash) {
        LocalDateTime expiresAt = revoked.get(tokenHash);
        if (expiresAt == null) {
            return false;
        }
        // Token hết hạn tự nhiên thì filter chặn sẵn — bỏ khỏi cache cho nhẹ.
        if (expiresAt.isBefore(LocalDateTime.now())) {
            revoked.remove(tokenHash);
            return false;
        }
        return true;
    }

    public void remember(String tokenHash, LocalDateTime expiresAt) {
        revoked.put(tokenHash, expiresAt);
    }

    /** Dọn các entry đã hết hạn; trả về số entry còn lại. */
    public int purgeExpired() {
        LocalDateTime now = LocalDateTime.now();
        revoked.values().removeIf(expiresAt -> expiresAt.isBefore(now));
        return revoked.size();
    }

    public int size() {
        return revoked.size();
    }
}
