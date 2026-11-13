package models.quangndth086311;

import java.time.LocalDate;

/**
 * Lô hàng: mỗi dòng nhập sinh đúng 1 lô
 * <p>Bảng: lots</p>
 */
public class Lot {

    public static final String TABLE = "lots";
    public static final String[] PRIMARY_KEY = {"lot_id"};

    /** lot_id | BIGINT | PK | tự tăng */
    private Long lotId;
    /** purchase_line_id | BIGINT | FK → purchase_invoice_lines.purchase_line_id | NOT NULL */
    private Long purchaseLineId;
    /** lot_code | d_code | NOT NULL */
    private String lotCode;
    /** roast_date | DATE */
    private LocalDate roastDate;
    /** expiry_date | DATE | NOT NULL */
    private LocalDate expiryDate;

    public Lot() {}

    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }
    public Long getPurchaseLineId() { return purchaseLineId; }
    public void setPurchaseLineId(Long purchaseLineId) { this.purchaseLineId = purchaseLineId; }
    public String getLotCode() { return lotCode; }
    public void setLotCode(String lotCode) { this.lotCode = lotCode; }
    public LocalDate getRoastDate() { return roastDate; }
    public void setRoastDate(LocalDate roastDate) { this.roastDate = roastDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    @Override
    public String toString() {
        return "Lot{" + "lotId=" + lotId + ", purchaseLineId=" + purchaseLineId + ", lotCode=" + lotCode + ", roastDate=" + roastDate + ", expiryDate=" + expiryDate + "}";
    }
}
