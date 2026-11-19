package models.longdtta00178;

import java.math.BigDecimal;

/**
 * 1 gói bán hiển thị trong thẻ ở màn bán hàng (không phải bảng DB, là kết quả câu SELECT có JOIN).
 * Nguồn: packages + grind_sizes + v_package_current_price + tồn các lô còn hạn.
 */
public class SellablePackage {

    private Integer packageId;
    private String sku;
    private Integer weightG;
    private String grindName;
    private Boolean wholeBean;
    private Integer minStockLevel;
    /** Giá hiện hành, null = chưa có giá (không cho bán). */
    private BigDecimal salePrice;
    /** Tồn bán được = tổng tồn các lô CÒN HẠN. */
    private Integer sellableQty;
    /** Có lô còn hàng sắp hết hạn (hệ thống sẽ trừ lô này trước). */
    private Boolean nearExpiry;
        /** Lô còn hạn, còn hàng: {lotId, lotCode, expiryDate, qty, nearExpiry}, hạn gần nhất trước */
    private java.util.List<java.util.Map<String, Object>> lots = new java.util.ArrayList<>();
    /** Lô khớp từ khóa tìm kiếm (tìm theo mã lô) -> ô chọn lô chọn sẵn lô này */
    private Long matchedLotId;
        /** Lô khớp từ khóa nhưng không bán được (hết hàng / hết hạn) -> nhắc người bán */
    private String matchedLotNote;

    public SellablePackage() {}

    /** Tồn còn > 0 nhưng <= mức tồn tối thiểu. */
    public Boolean getLowStock() {
        return sellableQty != null && minStockLevel != null && sellableQty > 0 && sellableQty <= minStockLevel;
    }

    /** Bán được: có giá và còn hàng. */
    public Boolean getCanSell() {
        return salePrice != null && sellableQty != null && sellableQty > 0;
    }

    

    public Integer getPackageId() { return packageId; }
    public void setPackageId(Integer packageId) { this.packageId = packageId; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public Integer getWeightG() { return weightG; }
    public void setWeightG(Integer weightG) { this.weightG = weightG; }
    public String getGrindName() { return grindName; }
    public void setGrindName(String grindName) { this.grindName = grindName; }
    public Boolean getWholeBean() { return wholeBean; }
    public void setWholeBean(Boolean wholeBean) { this.wholeBean = wholeBean; }
    public Integer getMinStockLevel() { return minStockLevel; }
    public void setMinStockLevel(Integer minStockLevel) { this.minStockLevel = minStockLevel; }
    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }
    public Integer getSellableQty() { return sellableQty; }
    public void setSellableQty(Integer sellableQty) { this.sellableQty = sellableQty; }
        public java.util.List<java.util.Map<String, Object>> getLots() { return lots; }
            public String getMatchedLotNote() { return matchedLotNote; }
    public void setMatchedLotNote(String matchedLotNote) { this.matchedLotNote = matchedLotNote; }
    public Long getMatchedLotId() { return matchedLotId; }
    public void setMatchedLotId(Long matchedLotId) { this.matchedLotId = matchedLotId; }
    public Boolean getNearExpiry() { return nearExpiry; }
    public void setNearExpiry(Boolean nearExpiry) { this.nearExpiry = nearExpiry; }
}
