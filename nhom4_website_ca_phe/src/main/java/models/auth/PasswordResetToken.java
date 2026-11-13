package models.auth;

import java.time.OffsetDateTime;

/**
 * Mã khôi phục mật khẩu gửi qua email
 * <p>Bảng: password_reset_tokens</p>
 */
public class PasswordResetToken {

    public static final String TABLE = "password_reset_tokens";
    public static final String[] PRIMARY_KEY = {"token_id"};

    /** token_id | BIGINT | PK | tự tăng */
    private Long tokenId;
    /** user_id | INT | FK → users.user_id | NOT NULL */
    private Integer userId;
    /** token_hash | VARCHAR(255) | NOT NULL */
    private String tokenHash;
    /** expires_at | TIMESTAMPTZ | NOT NULL */
    private OffsetDateTime expiresAt;
    /** used_at | TIMESTAMPTZ */
    private OffsetDateTime usedAt;
    /** created_at | TIMESTAMPTZ | NOT NULL */
    private OffsetDateTime createdAt;

    public PasswordResetToken() {}

    public Long getTokenId() { return tokenId; }
    public void setTokenId(Long tokenId) { this.tokenId = tokenId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(OffsetDateTime usedAt) { this.usedAt = usedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "PasswordResetToken{" + "tokenId=" + tokenId + ", userId=" + userId + ", expiresAt=" + expiresAt + ", usedAt=" + usedAt + ", createdAt=" + createdAt + "}";
    }
}
