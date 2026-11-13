package models.hieulnmth084702;

import java.time.OffsetDateTime;

import models.enums.NotificationType;

/**
 * Thông báo hệ thống (cận hạn, hết hạn, tồn thấp)
 * <p>Bảng: notifications</p>
 */
public class Notification {

    public static final String TABLE = "notifications";
    public static final String[] PRIMARY_KEY = {"notification_id"};

    /** notification_id | BIGINT | PK | tự tăng */
    private Long notificationId;
    /** notification_type | VARCHAR(12) | NOT NULL */
    private NotificationType notificationType;
    /** lot_id | BIGINT | FK → lots.lot_id */
    private Long lotId;
    /** package_id | INT | FK → packages.package_id */
    private Integer packageId;
    /** message | d_note | NOT NULL */
    private String message;
    /** created_at | TIMESTAMPTZ | NOT NULL */
    private OffsetDateTime createdAt;

    public Notification() {}

    public Long getNotificationId() { return notificationId; }
    public void setNotificationId(Long notificationId) { this.notificationId = notificationId; }
    public NotificationType getNotificationType() { return notificationType; }
    public void setNotificationType(NotificationType notificationType) { this.notificationType = notificationType; }
    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }
    public Integer getPackageId() { return packageId; }
    public void setPackageId(Integer packageId) { this.packageId = packageId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Notification{" + "notificationId=" + notificationId + ", notificationType=" + notificationType + ", lotId=" + lotId + ", packageId=" + packageId + ", message=" + message + ", createdAt=" + createdAt + "}";
    }
}
