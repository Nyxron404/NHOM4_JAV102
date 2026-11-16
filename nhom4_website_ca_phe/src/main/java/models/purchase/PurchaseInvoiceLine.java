package models.purchase;

import java.math.BigDecimal;

/**
 * Dòng hóa đơn nhập
 * <p>Bảng: purchase_invoice_lines</p>
 */
public class PurchaseInvoiceLine {

    public static final String TABLE = "purchase_invoice_lines";
    public static final String[] PRIMARY_KEY = {"purchase_line_id"};

    /** purchase_line_id | BIGINT | PK | tự tăng */
    private Long purchaseLineId;
    /** purchase_invoice_id | BIGINT | FK → purchase_invoices.purchase_invoice_id | NOT NULL */
    private Long purchaseInvoiceId;
    /** line_no | SMALLINT | NOT NULL */
    private Integer lineNo;
    /** package_id | INT | FK → packages.package_id | NOT NULL */
    private Integer packageId;
    /** supplier_item_code | VARCHAR(50) */
    private String supplierItemCode;
    /** supplier_item_name | d_name */
    private String supplierItemName;
    /** quantity | INT | NOT NULL */
    private Integer quantity;
    /** unit_cost | NUMERIC(14,0) | NOT NULL */
    private BigDecimal unitCost;

    public PurchaseInvoiceLine() {}

    public Long getPurchaseLineId() { return purchaseLineId; }
    public void setPurchaseLineId(Long purchaseLineId) { this.purchaseLineId = purchaseLineId; }
    public Long getPurchaseInvoiceId() { return purchaseInvoiceId; }
    public void setPurchaseInvoiceId(Long purchaseInvoiceId) { this.purchaseInvoiceId = purchaseInvoiceId; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public Integer getPackageId() { return packageId; }
    public void setPackageId(Integer packageId) { this.packageId = packageId; }
    public String getSupplierItemCode() { return supplierItemCode; }
    public void setSupplierItemCode(String supplierItemCode) { this.supplierItemCode = supplierItemCode; }
    public String getSupplierItemName() { return supplierItemName; }
    public void setSupplierItemName(String supplierItemName) { this.supplierItemName = supplierItemName; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

    @Override
    public String toString() {
        return "PurchaseInvoiceLine{" + "purchaseLineId=" + purchaseLineId + ", purchaseInvoiceId=" + purchaseInvoiceId + ", lineNo=" + lineNo + ", packageId=" + packageId + ", supplierItemCode=" + supplierItemCode + ", supplierItemName=" + supplierItemName + ", quantity=" + quantity + ", unitCost=" + unitCost + "}";
    }
}
