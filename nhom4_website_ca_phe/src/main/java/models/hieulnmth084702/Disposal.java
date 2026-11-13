package models.hieulnmth084702;

import java.time.OffsetDateTime;

/**
 * Phiếu xuất hủy (lô hết hạn, hỏng)
 * <p>Bảng: disposals</p>
 */
public class Disposal {

    public static final String TABLE = "disposals";
    public static final String[] PRIMARY_KEY = {"disposal_id"};

    /** disposal_id | BIGINT | PK | tự tăng */
    private Long disposalId;
    /** created_by | INT | FK → users.user_id | NOT NULL */
    private Integer createdBy;
    /** disposed_at | TIMESTAMPTZ | NOT NULL */
    private OffsetDateTime disposedAt;
    /** disposal_reason_id | INT | FK → disposal_reasons.disposal_reason_id | NOT NULL */
    private Integer disposalReasonId;
    /** note | d_note */
    private String note;

    public Disposal() {}

    public Long getDisposalId() { return disposalId; }
    public void setDisposalId(Long disposalId) { this.disposalId = disposalId; }
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getDisposedAt() { return disposedAt; }
    public void setDisposedAt(OffsetDateTime disposedAt) { this.disposedAt = disposedAt; }
    public Integer getDisposalReasonId() { return disposalReasonId; }
    public void setDisposalReasonId(Integer disposalReasonId) { this.disposalReasonId = disposalReasonId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    @Override
    public String toString() {
        return "Disposal{" + "disposalId=" + disposalId + ", createdBy=" + createdBy + ", disposedAt=" + disposedAt + ", disposalReasonId=" + disposalReasonId + ", note=" + note + "}";
    }
}
