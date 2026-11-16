package models.view;

import models.enums.PurchaseInvoiceStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

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
    /** confirmed_at | TIMESTAMPTZ */
    private OffsetDateTime confirmedAt;
    /** supplier_id | INT */
    private Integer supplierId;

    public PurchaseInvoiceTotal() {}

    public Long getPurchaseInvoiceId() { return purchaseInvoiceId; }
    public void setPurchaseInvoiceId(Long purchaseInvoiceId) { this.purchaseInvoiceId = purchaseInvoiceId; }
    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
    public PurchaseInvoiceStatus getStatus() { return status; }
    public void setStatus(PurchaseInvoiceStatus status) { this.status = status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(OffsetDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public Integer getSupplierId() { return supplierId; }
    public void setSupplierId(Integer supplierId) { this.supplierId = supplierId; }

    @Override
    public String toString() {
        return "PurchaseInvoiceTotal{" + "purchaseInvoiceId=" + purchaseInvoiceId + ", invoiceNo=" + invoiceNo + ", status=" + status + ", totalAmount=" + totalAmount + ", confirmedAt=" + confirmedAt + ", supplierId=" + supplierId + "}";
    }
}
