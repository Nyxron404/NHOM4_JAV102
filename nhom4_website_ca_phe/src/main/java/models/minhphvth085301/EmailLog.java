package models.minhphvth085301;

import java.time.OffsetDateTime;

import models.enums.EmailStatus;

/**
 * Email đã gửi qua Brevo
 * <p>Bảng: email_logs</p>
 */
public class EmailLog {

    public static final String TABLE = "email_logs";
    public static final String[] PRIMARY_KEY = {"email_log_id"};

    /** email_log_id | BIGINT | PK | tự tăng */
    private Long emailLogId;
    /** user_id | INT | FK → users.user_id */
    private Integer userId;
    /** to_email | d_email | NOT NULL */
    private String toEmail;
    /** template_code | d_code | NOT NULL */
    private String templateCode;
    /** provider_message_id | VARCHAR(100) */
    private String providerMessageId;
    /** status | VARCHAR(10) | NOT NULL */
    private EmailStatus status;
    /** error_message | VARCHAR(500) */
    private String errorMessage;
    /** sent_at | TIMESTAMPTZ | NOT NULL */
    private OffsetDateTime sentAt;

    public EmailLog() {}

    public Long getEmailLogId() { return emailLogId; }
    public void setEmailLogId(Long emailLogId) { this.emailLogId = emailLogId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getToEmail() { return toEmail; }
    public void setToEmail(String toEmail) { this.toEmail = toEmail; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
    public EmailStatus getStatus() { return status; }
    public void setStatus(EmailStatus status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public OffsetDateTime getSentAt() { return sentAt; }
    public void setSentAt(OffsetDateTime sentAt) { this.sentAt = sentAt; }

    @Override
    public String toString() {
        return "EmailLog{" + "emailLogId=" + emailLogId + ", userId=" + userId + ", toEmail=" + toEmail + ", templateCode=" + templateCode + ", providerMessageId=" + providerMessageId + ", status=" + status + ", errorMessage=" + errorMessage + ", sentAt=" + sentAt + "}";
    }
}
