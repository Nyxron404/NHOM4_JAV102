package models.view;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tồn hiện tại theo lô = tổng sổ cái stock_movements (dữ liệu dẫn xuất, không INSERT/UPDATE)
 * <p>View (chỉ đọc): v_lot_stock</p>
 */
public class LotStock {

    public static final String VIEW = "v_lot_stock";

    /** lot_id | BIGINT */
    private Long lotId;
    /** lot_code | d_code */
    private String lotCode;
    /** package_id | INT */
    private Integer packageId;
    /** roast_date | DATE */
    private LocalDate roastDate;
    /** expiry_date | DATE */
    private LocalDate expiryDate;
    /** unit_cost | NUMERIC(14,0) */
    private BigDecimal unitCost;
    /** quantity_on_hand | BIGINT | SUM */
    private Long quantityOnHand;

    public LotStock() {}

    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }
    public String getLotCode() { return lotCode; }
    public void setLotCode(String lotCode) { this.lotCode = lotCode; }
    public Integer getPackageId() { return packageId; }
    public void setPackageId(Integer packageId) { this.packageId = packageId; }
    public LocalDate getRoastDate() { return roastDate; }
    public void setRoastDate(LocalDate roastDate) { this.roastDate = roastDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public Long getQuantityOnHand() { return quantityOnHand; }
    public void setQuantityOnHand(Long quantityOnHand) { this.quantityOnHand = quantityOnHand; }

    @Override
    public String toString() {
        return "LotStock{" + "lotId=" + lotId + ", lotCode=" + lotCode + ", packageId=" + packageId + ", roastDate=" + roastDate + ", expiryDate=" + expiryDate + ", unitCost=" + unitCost + ", quantityOnHand=" + quantityOnHand + "}";
    }
}
