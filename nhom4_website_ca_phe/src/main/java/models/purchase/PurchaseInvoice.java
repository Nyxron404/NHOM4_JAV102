package models.purchase;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import models.enums.InputMethod;
import models.enums.PurchaseInvoiceStatus;

/**
 * Hóa đơn nhập (QR / mã tra cứu / nhập tay)
 * <p>Bảng: purchase_invoices</p>
 */
public class PurchaseInvoice {

    public static final String TABLE = "purchase_invoices";
    public static final String[] PRIMARY_KEY = {"purchase_invoice_id"};

    /** purchase_invoice_id | BIGINT | PK | tự tăng */
    private Long purchaseInvoiceId;
    /** supplier_id | INT | FK → suppliers.supplier_id | NOT NULL */
    private Integer supplierId;
    /** invoice_no | VARCHAR(50) | NOT NULL */
    private String invoiceNo;
    /** lookup_code | VARCHAR(50) */
    private String lookupCode;
    /** invoice_date | DATE | NOT NULL */
    private LocalDate invoiceDate;
    /** input_method | VARCHAR(10) | NOT NULL */
    private InputMethod inputMethod;
    /** status | VARCHAR(10) | NOT NULL */
    private PurchaseInvoiceStatus status = PurchaseInvoiceStatus.DRAFT;
    /** created_by | INT | FK → users.user_id | NOT NULL */
    private Integer createdBy;
    /** created_at | TIMESTAMPTZ | NOT NULL */
    private OffsetDateTime createdAt;
    /** confirmed_at | TIMESTAMPTZ */
    private OffsetDateTime confirmedAt;

    public PurchaseInvoice() {}

    public Long getPurchaseInvoiceId() { return purchaseInvoiceId; }
    public void setPurchaseInvoiceId(Long purchaseInvoiceId) { this.purchaseInvoiceId = purchaseInvoiceId; }
    public Integer getSupplierId() { return supplierId; }
    public void setSupplierId(Integer supplierId) { this.supplierId = supplierId; }
    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
    public String getLookupCode() { return lookupCode; }
    public void setLookupCode(String lookupCode) { this.lookupCode = lookupCode; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }
    public InputMethod getInputMethod() { return inputMethod; }
    public void setInputMethod(InputMethod inputMethod) { this.inputMethod = inputMethod; }
    public PurchaseInvoiceStatus getStatus() { return status; }
    public void setStatus(PurchaseInvoiceStatus status) { this.status = status; }
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(OffsetDateTime confirmedAt) { this.confirmedAt = confirmedAt; }

    @Override
    public String toString() {
        return "PurchaseInvoice{" + "purchaseInvoiceId=" + purchaseInvoiceId + ", supplierId=" + supplierId + ", invoiceNo=" + invoiceNo + ", lookupCode=" + lookupCode + ", invoiceDate=" + invoiceDate + ", inputMethod=" + inputMethod + ", status=" + status + ", createdBy=" + createdBy + ", createdAt=" + createdAt + ", confirmedAt=" + confirmedAt + "}";
    }
}
