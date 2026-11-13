package models.hieuttth082971;

import java.math.BigDecimal;

import models.enums.PurchaseInvoiceStatus;

/**
 * Tổng tiền hóa đơn nhập (dữ liệu dẫn xuất, không INSERT/UPDATE)
 * <p>View (chỉ đọc): v_purchase_invoice_totals</p>
 */
public class PurchaseInvoiceTotal {

    public static final String VIEW = "v_purchase_invoice_totals";

    /** purchase_invoice_id | BIGINT */
    private Long purchaseInvoiceId;
    /** invoice_no | VARCHAR(50) */
    private String invoiceNo;
    /** status | VARCHAR | PurchaseInvoiceStatus */
    private PurchaseInvoiceStatus status;
    /** total_amount | NUMERIC */
    private BigDecimal totalAmount;

    public PurchaseInvoiceTotal() {}

    public Long getPurchaseInvoiceId() { return purchaseInvoiceId; }
    public void setPurchaseInvoiceId(Long purchaseInvoiceId) { this.purchaseInvoiceId = purchaseInvoiceId; }
    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
    public PurchaseInvoiceStatus getStatus() { return status; }
    public void setStatus(PurchaseInvoiceStatus status) { this.status = status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    @Override
    public String toString() {
        return "PurchaseInvoiceTotal{" + "purchaseInvoiceId=" + purchaseInvoiceId + ", invoiceNo=" + invoiceNo + ", status=" + status + ", totalAmount=" + totalAmount + "}";
    }
}
