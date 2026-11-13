package models.longdtta001781;

import java.math.BigDecimal;

/**
 * Chi tiết hóa đơn bán
 * <p>Bảng: sales_invoice_lines</p>
 */
public class SalesInvoiceLine {

    public static final String TABLE = "sales_invoice_lines";
    public static final String[] PRIMARY_KEY = {"sales_line_id"};

    /** sales_line_id | BIGINT | PK | tự tăng */
    private Long salesLineId;
    /** sales_invoice_id | BIGINT | FK → sales_invoices.sales_invoice_id | NOT NULL */
    private Long salesInvoiceId;
    /** package_id | INT | FK → packages.package_id | NOT NULL */
    private Integer packageId;
    /** quantity | INT | NOT NULL */
    private Integer quantity;
    /** unit_price | NUMERIC(14,0) | NOT NULL */
    private BigDecimal unitPrice;

    public SalesInvoiceLine() {}

    public Long getSalesLineId() { return salesLineId; }
    public void setSalesLineId(Long salesLineId) { this.salesLineId = salesLineId; }
    public Long getSalesInvoiceId() { return salesInvoiceId; }
    public void setSalesInvoiceId(Long salesInvoiceId) { this.salesInvoiceId = salesInvoiceId; }
    public Integer getPackageId() { return packageId; }
    public void setPackageId(Integer packageId) { this.packageId = packageId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    @Override
    public String toString() {
        return "SalesInvoiceLine{" + "salesLineId=" + salesLineId + ", salesInvoiceId=" + salesInvoiceId + ", packageId=" + packageId + ", quantity=" + quantity + ", unitPrice=" + unitPrice + "}";
    }
}
