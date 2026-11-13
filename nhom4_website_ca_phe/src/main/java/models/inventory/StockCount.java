package models.inventory;

import java.time.OffsetDateTime;

import models.enums.StockCountStatus;
import models.enums.StockCountType;

/**
 * Phiếu kiểm kê / điều chỉnh tồn
 * <p>Bảng: stock_counts</p>
 */
public class StockCount {

    public static final String TABLE = "stock_counts";
    public static final String[] PRIMARY_KEY = {"stock_count_id"};

    /** stock_count_id | BIGINT | PK | tự tăng */
    private Long stockCountId;
    /** count_type | VARCHAR(12) | NOT NULL */
    private StockCountType countType;
    /** status | VARCHAR(10) | NOT NULL */
    private StockCountStatus status = StockCountStatus.PENDING;
    /** created_by | INT | FK → users.user_id | NOT NULL */
    private Integer createdBy;
    /** created_at | TIMESTAMPTZ | NOT NULL */
    private OffsetDateTime createdAt;
    /** reviewed_by | INT | FK → users.user_id */
    private Integer reviewedBy;
    /** reviewed_at | TIMESTAMPTZ */
    private OffsetDateTime reviewedAt;
    /** note | d_note */
    private String note;

    public StockCount() {}

    public Long getStockCountId() { return stockCountId; }
    public void setStockCountId(Long stockCountId) { this.stockCountId = stockCountId; }
    public StockCountType getCountType() { return countType; }
    public void setCountType(StockCountType countType) { this.countType = countType; }
    public StockCountStatus getStatus() { return status; }
    public void setStatus(StockCountStatus status) { this.status = status; }
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Integer reviewedBy) { this.reviewedBy = reviewedBy; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    @Override
    public String toString() {
        return "StockCount{" + "stockCountId=" + stockCountId + ", countType=" + countType + ", status=" + status + ", createdBy=" + createdBy + ", createdAt=" + createdAt + ", reviewedBy=" + reviewedBy + ", reviewedAt=" + reviewedAt + ", note=" + note + "}";
    }
}
