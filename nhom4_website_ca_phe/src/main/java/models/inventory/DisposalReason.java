package models.inventory;
/**
 * Lý do xuất hủy (hết hạn, hỏng bao bì...)
 * <p>Bảng: disposal_reasons</p>
 */
public class DisposalReason {

    public static final String TABLE = "disposal_reasons";
    public static final String[] PRIMARY_KEY = {"disposal_reason_id"};

    /** disposal_reason_id | INT | PK | tự tăng */
    private Integer disposalReasonId;
    /** reason_code | d_code | NOT NULL */
    private String reasonCode;
    /** reason_name | d_name | NOT NULL */
    private String reasonName;
    /** counts_as_loss | BOOLEAN | NOT NULL */
    private Boolean countsAsLoss;

    public DisposalReason() {}

    public Integer getDisposalReasonId() { return disposalReasonId; }
    public void setDisposalReasonId(Integer disposalReasonId) { this.disposalReasonId = disposalReasonId; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getReasonName() { return reasonName; }
    public void setReasonName(String reasonName) { this.reasonName = reasonName; }
    public Boolean getCountsAsLoss() { return countsAsLoss; }
    public void setCountsAsLoss(Boolean countsAsLoss) { this.countsAsLoss = countsAsLoss; }

    @Override
    public String toString() {
        return "DisposalReason{" + "disposalReasonId=" + disposalReasonId + ", reasonCode=" + reasonCode + ", reasonName=" + reasonName + ", countsAsLoss=" + countsAsLoss + "}";
    }
}
