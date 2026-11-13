package models.view;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import models.enums.SalesInvoiceStatus;

/**
 * Tổng tiền hóa đơn bán (dữ liệu dẫn xuất, không INSERT/UPDATE)
 * <p>View (chỉ đọc): v_sales_invoice_totals</p>
 */
public class SalesInvoiceTotal {

    public static final String VIEW = "v_sales_invoice_totals";

    /** sales_invoice_id | BIGINT */
    private Long salesInvoiceId;
    /** invoice_no | d_code */
    private String invoiceNo;
    /** status | VARCHAR | SalesInvoiceStatus */
    private SalesInvoiceStatus status;
    /** created_at | TIMESTAMPTZ */
    private OffsetDateTime createdAt;
    /** total_amount | NUMERIC */
    private BigDecimal totalAmount;

    public SalesInvoiceTotal() {}

    public Long getSalesInvoiceId() { return salesInvoiceId; }
    public void setSalesInvoiceId(Long salesInvoiceId) { this.salesInvoiceId = salesInvoiceId; }
    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
    public SalesInvoiceStatus getStatus() { return status; }
    public void setStatus(SalesInvoiceStatus status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    @Override
    public String toString() {
        return "SalesInvoiceTotal{" + "salesInvoiceId=" + salesInvoiceId + ", invoiceNo=" + invoiceNo + ", status=" + status + ", createdAt=" + createdAt + ", totalAmount=" + totalAmount + "}";
    }
}
