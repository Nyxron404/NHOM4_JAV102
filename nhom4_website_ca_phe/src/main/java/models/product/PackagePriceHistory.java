package models.product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Lịch sử giá bán; giá hiện hành = bản ghi hiệu lực mới nhất
 * <p>Bảng: package_price_histories</p>
 */
public class PackagePriceHistory {

    public static final String TABLE = "package_price_histories";
    public static final String[] PRIMARY_KEY = {"price_history_id"};

    /** price_history_id | BIGINT | PK | tự tăng */
    private Long priceHistoryId;
    /** package_id | INT | FK → packages.package_id | NOT NULL */
    private Integer packageId;
    /** sale_price | NUMERIC(14,0) | NOT NULL */
    private BigDecimal salePrice;
    /** effective_from | TIMESTAMPTZ | NOT NULL */
    private OffsetDateTime effectiveFrom;
    /** changed_by | INT | FK → users.user_id | NOT NULL */
    private Integer changedBy;
    /** note | d_note */
    private String note;

    public PackagePriceHistory() {}

    public Long getPriceHistoryId() { return priceHistoryId; }
    public void setPriceHistoryId(Long priceHistoryId) { this.priceHistoryId = priceHistoryId; }
    public Integer getPackageId() { return packageId; }
    public void setPackageId(Integer packageId) { this.packageId = packageId; }
    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }
    public OffsetDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(OffsetDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public Integer getChangedBy() { return changedBy; }
    public void setChangedBy(Integer changedBy) { this.changedBy = changedBy; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    @Override
    public String toString() {
        return "PackagePriceHistory{" + "priceHistoryId=" + priceHistoryId + ", packageId=" + packageId + ", salePrice=" + salePrice + ", effectiveFrom=" + effectiveFrom + ", changedBy=" + changedBy + ", note=" + note + "}";
    }
}
