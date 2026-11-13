package models.view;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Giá trị lỗ của phiếu xuất hủy = SL hủy x giá nhập của lô (dữ liệu dẫn xuất, không INSERT/UPDATE)
 * <p>View (chỉ đọc): v_disposal_losses</p>
 */
public class DisposalLoss {

    public static final String VIEW = "v_disposal_losses";

    /** disposal_id | BIGINT */
    private Long disposalId;
    /** disposed_at | TIMESTAMPTZ */
    private OffsetDateTime disposedAt;
    /** disposal_reason_id | INT */
    private Integer disposalReasonId;
    /** loss_amount | NUMERIC */
    private BigDecimal lossAmount;

    public DisposalLoss() {}

    public Long getDisposalId() { return disposalId; }
    public void setDisposalId(Long disposalId) { this.disposalId = disposalId; }
    public OffsetDateTime getDisposedAt() { return disposedAt; }
    public void setDisposedAt(OffsetDateTime disposedAt) { this.disposedAt = disposedAt; }
    public Integer getDisposalReasonId() { return disposalReasonId; }
    public void setDisposalReasonId(Integer disposalReasonId) { this.disposalReasonId = disposalReasonId; }
    public BigDecimal getLossAmount() { return lossAmount; }
    public void setLossAmount(BigDecimal lossAmount) { this.lossAmount = lossAmount; }

    @Override
    public String toString() {
        return "DisposalLoss{" + "disposalId=" + disposalId + ", disposedAt=" + disposedAt + ", disposalReasonId=" + disposalReasonId + ", lossAmount=" + lossAmount + "}";
    }
}
