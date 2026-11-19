package models.longdtta00178;
import java.time.LocalDate;
/**
 * Chi tiết trừ kho: dòng bán lấy từ lô nào (FEFO)
 * <p>Bảng: sales_line_allocations</p>
 */
public class SalesLineAllocation {

    public static final String TABLE = "sales_line_allocations";
    public static final String[] PRIMARY_KEY = {"allocation_id"};

    /** allocation_id | BIGINT | PK | tự tăng */
    private Long allocationId;
    /** sales_line_id | BIGINT | FK → sales_invoice_lines.sales_line_id | NOT NULL */
    private Long salesLineId;
    /** lot_id | BIGINT | FK → lots.lot_id | NOT NULL */
    private Long lotId;
    /** quantity | INT | NOT NULL */
    private Integer quantity;
        // ---- Thông tin hiển thị (tính từ JOIN, không lưu DB) ----
    private String variantName;
    private Integer weightG;
    private Boolean wholeBean;
    private String lotCode;
    private LocalDate expiryDate;
    /** Lô sắp hết hạn (trong số ngày inventory.near_expiry_days) */
    private Boolean nearExpiry;
        /** Số túi được quét QR (cho phép bán âm phần này) */
    private Integer scannedQty;
    /** Tồn hiện tại của lô (đơn chờ: để cảnh báo vượt tồn) */
    private Integer lotStock;

    public SalesLineAllocation() {}

    public Long getAllocationId() { return allocationId; }
    public void setAllocationId(Long allocationId) { this.allocationId = allocationId; }
    public Long getSalesLineId() { return salesLineId; }
    public void setSalesLineId(Long salesLineId) { this.salesLineId = salesLineId; }
    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getVariantName() { return variantName; }
    public void setVariantName(String variantName) { this.variantName = variantName; }
    public Integer getWeightG() { return weightG; }
    public void setWeightG(Integer weightG) { this.weightG = weightG; }
    public Boolean getWholeBean() { return wholeBean; }
    public void setWholeBean(Boolean wholeBean) { this.wholeBean = wholeBean; }
    public String getLotCode() { return lotCode; }
    public void setLotCode(String lotCode) { this.lotCode = lotCode; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
        public Integer getScannedQty() { return scannedQty; }
    public void setScannedQty(Integer scannedQty) { this.scannedQty = scannedQty; }
    public Integer getLotStock() { return lotStock; }
    public void setLotStock(Integer lotStock) { this.lotStock = lotStock; }
    public Boolean getNearExpiry() { return nearExpiry; }
    public void setNearExpiry(Boolean nearExpiry) { this.nearExpiry = nearExpiry; }

    @Override
    public String toString() {
        return "SalesLineAllocation{" + "allocationId=" + allocationId + ", salesLineId=" + salesLineId + ", lotId=" + lotId + ", quantity=" + quantity + "}";
    }
}
