package models.longdtta001781;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import models.enums.PaymentStatus;

/**
 * Giao dịch thanh toán của hóa đơn
 * <p>Bảng: payments</p>
 */
public class Payment {

    public static final String TABLE = "payments";
    public static final String[] PRIMARY_KEY = {"payment_id"};

    /** payment_id | BIGINT | PK | tự tăng */
    private Long paymentId;
    /** sales_invoice_id | BIGINT | FK → sales_invoices.sales_invoice_id | NOT NULL */
    private Long salesInvoiceId;
    /** payment_method_id | INT | FK → payment_methods.payment_method_id | NOT NULL */
    private Integer paymentMethodId;
    /** amount | NUMERIC(14,0) | NOT NULL */
    private BigDecimal amount;
    /** status | VARCHAR(10) | NOT NULL */
    private PaymentStatus status = PaymentStatus.PENDING;
    /** confirmed_by | INT | FK → users.user_id */
    private Integer confirmedBy;
    /** created_at | TIMESTAMPTZ | NOT NULL */
    private OffsetDateTime createdAt;
    /** paid_at | TIMESTAMPTZ */
    private OffsetDateTime paidAt;

    public Payment() {}

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public Long getSalesInvoiceId() { return salesInvoiceId; }
    public void setSalesInvoiceId(Long salesInvoiceId) { this.salesInvoiceId = salesInvoiceId; }
    public Integer getPaymentMethodId() { return paymentMethodId; }
    public void setPaymentMethodId(Integer paymentMethodId) { this.paymentMethodId = paymentMethodId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public Integer getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(Integer confirmedBy) { this.confirmedBy = confirmedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(OffsetDateTime paidAt) { this.paidAt = paidAt; }

    @Override
    public String toString() {
        return "Payment{" + "paymentId=" + paymentId + ", salesInvoiceId=" + salesInvoiceId + ", paymentMethodId=" + paymentMethodId + ", amount=" + amount + ", status=" + status + ", confirmedBy=" + confirmedBy + ", createdAt=" + createdAt + ", paidAt=" + paidAt + "}";
    }
}
