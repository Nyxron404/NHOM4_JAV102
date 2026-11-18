package dao.minhpvth08530;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Băm mật khẩu bằng PBKDF2-HMAC-SHA256 (có sẵn trong Java, không cần thư viện ngoài).
 * <p>Chuỗi lưu vào users.password_hash: {@code pbkdf2_sha256$<số vòng>$<salt base64>$<hash base64>}.
 * Lưu kèm số vòng và salt nên sau này tăng số vòng vẫn kiểm tra được mật khẩu cũ.</p>
 */
public final class PasswordHasher {

    private static final String PREFIX = "pbkdf2_sha256";
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {}

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);
        Base64.Encoder b64 = Base64.getEncoder().withoutPadding();
        return PREFIX + "$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
    }

    /** So khớp mật khẩu với chuỗi đã lưu; chuỗi sai định dạng thì trả false. */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);   // so sánh thời gian cố định
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** Quy tắc mật khẩu dùng chung cho đổi / đặt lại / tạo tài khoản. */
    public static void validate(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất 8 ký tự");
        }
        if (password.length() > 128) {
            throw new IllegalArgumentException("Mật khẩu tối đa 128 ký tự");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Mật khẩu phải có cả chữ và số");
        }
    }

    /** Mã ngẫu nhiên an toàn để gửi qua email (dạng base64 url-safe). */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Mật khẩu tạm khi chủ cửa hàng đặt lại mật khẩu cho tài khoản khác (UC14). */
    public static String randomPassword() {
        String letters = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
        String digits = "23456789";
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < 8; index++) {
            builder.append(letters.charAt(RANDOM.nextInt(letters.length())));
        }
        for (int index = 0; index < 2; index++) {
            // chèn số vào vị trí ngẫu nhiên để mật khẩu không có khuôn cố định
            builder.insert(RANDOM.nextInt(builder.length() + 1), digits.charAt(RANDOM.nextInt(digits.length())));
        }
        return builder.toString();
    }

    /** Băm SHA-256 (hex) — chỉ lưu bản băm của mã khôi phục, không lưu mã gốc. */
    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Không băm được mật khẩu", exception);
        } finally {
            spec.clearPassword();
        }
    }
}
