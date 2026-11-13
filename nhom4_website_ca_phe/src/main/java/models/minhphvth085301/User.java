package models.minhphvth085301;

import java.time.OffsetDateTime;

import models.enums.UserStatus;

/**
 * Người dùng: chủ cửa hàng + tài khoản quầy dùng chung
 * <p>Bảng: users</p>
 */
public class User {

    public static final String TABLE = "users";
    public static final String[] PRIMARY_KEY = {"user_id"};

    /** user_id | INT | PK | tự tăng */
    private Integer userId;
    /** role_id | INT | FK → roles.role_id | NOT NULL */
    private Integer roleId;
    /** username | d_username | NOT NULL */
    private String username;
    /** password_hash | VARCHAR(255) | NOT NULL */
    private String passwordHash;
    /** full_name | d_name | NOT NULL */
    private String fullName;
    /** email | d_email */
    private String email;
    /** phone | d_phone */
    private String phone;
    /** status | VARCHAR(10) | NOT NULL */
    private UserStatus status = UserStatus.ACTIVE;
    /** created_at | TIMESTAMPTZ | NOT NULL */
    private OffsetDateTime createdAt;
    /** last_login_at | TIMESTAMPTZ */
    private OffsetDateTime lastLoginAt;

    public User() {}

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public Integer getRoleId() { return roleId; }
    public void setRoleId(Integer roleId) { this.roleId = roleId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(OffsetDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    @Override
    public String toString() {
        return "User{" + "userId=" + userId + ", roleId=" + roleId + ", username=" + username + ", fullName=" + fullName + ", email=" + email + ", phone=" + phone + ", status=" + status + ", createdAt=" + createdAt + ", lastLoginAt=" + lastLoginAt + "}";
    }
}
