package models.inventory;

import java.time.OffsetDateTime;

/**
 * Trạng thái đã đọc theo từng CCH (N-N)
 * <p>Bảng: notification_reads</p>
 */
public class NotificationRead {

    public static final String TABLE = "notification_reads";
    public static final String[] PRIMARY_KEY = {"notification_id", "user_id"};

    /** notification_id | BIGINT | PK | FK → notifications.notification_id */
    private Long notificationId;
    /** user_id | INT | PK | FK → users.user_id */
    private Integer userId;
    /** read_at | TIMESTAMPTZ | NOT NULL */
    private OffsetDateTime readAt;

    public NotificationRead() {}

    public Long getNotificationId() { return notificationId; }
    public void setNotificationId(Long notificationId) { this.notificationId = notificationId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public OffsetDateTime getReadAt() { return readAt; }
    public void setReadAt(OffsetDateTime readAt) { this.readAt = readAt; }

    @Override
    public String toString() {
        return "NotificationRead{" + "notificationId=" + notificationId + ", userId=" + userId + ", readAt=" + readAt + "}";
    }
}
