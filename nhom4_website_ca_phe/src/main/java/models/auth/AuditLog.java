package models.auth;

import java.time.OffsetDateTime;

/**
 * Nhật ký thao tác nhạy cảm (sửa/hủy đơn, phân quyền...)
 * <p>Bảng: audit_logs</p>
 */
public class AuditLog {

    public static final String TABLE = "audit_logs";
    public static final String[] PRIMARY_KEY = {"audit_id"};

    /** audit_id | BIGINT | PK | tự tăng */
    private Long auditId;
    /** user_id | INT | FK → users.user_id */
    private Integer userId;
    /** action | VARCHAR(50) | NOT NULL */
    private String action;
    /** entity_type | VARCHAR(50) | NOT NULL */
    private String entityType;
    /** entity_id | BIGINT */
    private Long entityId;
    /** detail | JSONB */
    private String detail;
    /** created_at | TIMESTAMPTZ | NOT NULL */
    private OffsetDateTime createdAt;

    public AuditLog() {}
    

    public Long getAuditId() { return auditId; }
    public void setAuditId(Long auditId) { this.auditId = auditId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "AuditLog{" + "auditId=" + auditId + ", userId=" + userId + ", action=" + action + ", entityType=" + entityType + ", entityId=" + entityId + ", createdAt=" + createdAt + "}";
    }
}
