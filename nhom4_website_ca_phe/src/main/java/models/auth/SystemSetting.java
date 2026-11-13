package models.auth;

import java.time.OffsetDateTime;

/**
 * Cấu hình: thông tin cửa hàng, số ngày cận hạn...
 * <p>Bảng: system_settings</p>
 */
public class SystemSetting {

    public static final String TABLE = "system_settings";
    public static final String[] PRIMARY_KEY = {"setting_key"};

    /** setting_key | VARCHAR(60) | PK */
    private String settingKey;
    /** setting_value | TEXT | NOT NULL */
    private String settingValue;
    /** description | d_text */
    private String description;
    /** updated_by | INT | FK → users.user_id */
    private Integer updatedBy;
    /** updated_at | TIMESTAMPTZ | NOT NULL */
    private OffsetDateTime updatedAt;

    public SystemSetting() {}

    public String getSettingKey() { return settingKey; }
    public void setSettingKey(String settingKey) { this.settingKey = settingKey; }
    public String getSettingValue() { return settingValue; }
    public void setSettingValue(String settingValue) { this.settingValue = settingValue; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Integer updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "SystemSetting{" + "settingKey=" + settingKey + ", settingValue=" + settingValue + ", description=" + description + ", updatedBy=" + updatedBy + ", updatedAt=" + updatedAt + "}";
    }
}
