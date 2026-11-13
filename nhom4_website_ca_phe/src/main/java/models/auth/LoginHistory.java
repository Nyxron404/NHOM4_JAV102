package models.auth;

import java.time.OffsetDateTime;

/**
 * Lịch sử đăng nhập (máy quầy dùng chung)
 * <p>Bảng: login_histories</p>
 */
public class LoginHistory {

    public static final String TABLE = "login_histories";
    public static final String[] PRIMARY_KEY = {"login_id"};

    /** login_id | BIGINT | PK | tự tăng */
    private Long loginId;
    /** user_id | INT | FK → users.user_id */
    private Integer userId;
    /** attempted_username | VARCHAR(50) */
    private String attemptedUsername;
    /** is_success | BOOLEAN | NOT NULL */
    private Boolean isSuccess;
    /** ip_address | INET */
    private String ipAddress;
    /** user_agent | VARCHAR(255) */
    private String userAgent;
    /** logged_in_at | TIMESTAMPTZ | NOT NULL */
    private OffsetDateTime loggedInAt;
    /** logged_out_at | TIMESTAMPTZ */
    private OffsetDateTime loggedOutAt;

    public LoginHistory() {}

    public Long getLoginId() { return loginId; }
    public void setLoginId(Long loginId) { this.loginId = loginId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getAttemptedUsername() { return attemptedUsername; }
    public void setAttemptedUsername(String attemptedUsername) { this.attemptedUsername = attemptedUsername; }
    public Boolean getIsSuccess() { return isSuccess; }
    public void setIsSuccess(Boolean isSuccess) { this.isSuccess = isSuccess; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public OffsetDateTime getLoggedInAt() { return loggedInAt; }
    public void setLoggedInAt(OffsetDateTime loggedInAt) { this.loggedInAt = loggedInAt; }
    public OffsetDateTime getLoggedOutAt() { return loggedOutAt; }
    public void setLoggedOutAt(OffsetDateTime loggedOutAt) { this.loggedOutAt = loggedOutAt; }

    @Override
    public String toString() {
        return "LoginHistory{" + "loginId=" + loginId + ", userId=" + userId + ", attemptedUsername=" + attemptedUsername + ", isSuccess=" + isSuccess + ", ipAddress=" + ipAddress + ", userAgent=" + userAgent + ", loggedInAt=" + loggedInAt + ", loggedOutAt=" + loggedOutAt + "}";
    }
}
