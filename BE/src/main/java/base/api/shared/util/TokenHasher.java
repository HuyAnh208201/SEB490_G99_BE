package base.api.shared.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * JWT thô là credential, không lưu nguyên văn trong DB. Băm SHA-256 cho ra chuỗi
 * hex 64 ký tự, vừa đủ ngắn để đánh UNIQUE index trên MySQL (VARCHAR(1024) utf8mb4
 * vượt giới hạn 3072 byte của InnoDB).
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    public static String sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 không khả dụng", ex);
        }
    }
}
