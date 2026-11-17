package models.minhpvth08530;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Lịch sử giá bán; giá hiện hành = bản ghi hiệu lực mới nhất
 * <p>Bảng: package_price_histories</p>
 */
public class PackagePriceHistory {

    public static final String TABLE = "package_price_histories";
    public static final String[] PRIMARY_KEY = {"price_history_id"};

    private Long priceHistoryId;
    private Integer packageId;
    private BigDecimal salePrice;
    private OffsetDateTime effectiveFrom;
    private Integer changedBy;
    private String note;

    // Thông tin hiển thị (JOIN, không lưu DB)
    private String changedByName;
    private Boolean applied;
    private Boolean current;

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
    public String getChangedByName() { return changedByName; }
    public void setChangedByName(String changedByName) { this.changedByName = changedByName; }
    public Boolean getApplied() { return applied; }
    public void setApplied(Boolean applied) { this.applied = applied; }
    public Boolean getCurrent() { return current; }
    public void setCurrent(Boolean current) { this.current = current; }

    @Override
    public String toString() {
        return "PackagePriceHistory{" + "priceHistoryId=" + priceHistoryId + ", packageId=" + packageId + ", salePrice=" + salePrice + ", effectiveFrom=" + effectiveFrom + ", changedBy=" + changedBy + ", note=" + note + "}";
    }
}
