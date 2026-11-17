package models.longdtta00178;

import java.time.OffsetDateTime;

import models.enums.SalesInvoiceStatus;

import java.math.BigDecimal;

/**
 * Hóa đơn bán tại quầy
 * <p>Bảng: sales_invoices</p>
 */
public class SalesInvoice {

    public static final String TABLE = "sales_invoices";
    public static final String[] PRIMARY_KEY = {"sales_invoice_id"};

    /** sales_invoice_id | BIGINT | PK | tự tăng */
    private Long salesInvoiceId;
    /** invoice_no | d_code | NOT NULL */
    private String invoiceNo;
    /** created_by | INT | FK → users.user_id | NOT NULL */
    private Integer createdBy;
    /** created_at | TIMESTAMPTZ | NOT NULL */
    private OffsetDateTime createdAt;
    /** status | VARCHAR(16) | NOT NULL */
    private SalesInvoiceStatus status = SalesInvoiceStatus.PENDING;
    /** cancelled_by | INT | FK → users.user_id */
    private Integer cancelledBy;
    /** cancelled_at | TIMESTAMPTZ */
    private OffsetDateTime cancelledAt;
    /** cancel_reason | d_note */
    private String cancelReason;
    /** is_restocked | BOOLEAN */
    private Boolean isRestocked;

    // ---- Thông tin hiển thị (tính từ JOIN / SUM, không lưu DB) ----
    /** users.full_name của người tạo đơn */
    private String cashierName;
    /** Tổng tiền = SUM(quantity * unit_price) các dòng */
    private BigDecimal totalAmount = BigDecimal.ZERO;
    /** Đã thu = SUM(payments.amount) các giao dịch SUCCESS */
    private BigDecimal paidAmount = BigDecimal.ZERO;

    public SalesInvoice() {}

    public Long getSalesInvoiceId() { return salesInvoiceId; }
    public void setSalesInvoiceId(Long salesInvoiceId) { this.salesInvoiceId = salesInvoiceId; }
    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public SalesInvoiceStatus getStatus() { return status; }
    public void setStatus(SalesInvoiceStatus status) { this.status = status; }
    public Integer getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(Integer cancelledBy) { this.cancelledBy = cancelledBy; }
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(OffsetDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public Boolean getIsRestocked() { return isRestocked; }
    public void setIsRestocked(Boolean isRestocked) { this.isRestocked = isRestocked; }
        public String getCashierName() { return cashierName; }
    public void setCashierName(String cashierName) { this.cashierName = cashierName; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    /** Còn phải thu = tổng tiền - đã thu */
    public BigDecimal getDueAmount() { return totalAmount.subtract(paidAmount); }
    @Override
    public String toString() {
        return "SalesInvoice{" + "salesInvoiceId=" + salesInvoiceId + ", invoiceNo=" + invoiceNo + ", createdBy=" + createdBy + ", createdAt=" + createdAt + ", status=" + status + ", cancelledBy=" + cancelledBy + ", cancelledAt=" + cancelledAt + ", cancelReason=" + cancelReason + ", isRestocked=" + isRestocked + "}";
    }
}
