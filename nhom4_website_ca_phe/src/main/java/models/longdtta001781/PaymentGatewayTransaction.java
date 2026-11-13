package models.longdtta001781;

import java.time.OffsetDateTime;

/**
 * Thông tin riêng của giao dịch qua cổng (1-1 với payments)
 * <p>Bảng: payment_gateway_transactions</p>
 */
public class PaymentGatewayTransaction {

    public static final String TABLE = "payment_gateway_transactions";
    public static final String[] PRIMARY_KEY = {"payment_id"};

    /** payment_id | BIGINT | PK | FK → payments.payment_id */
    private Long paymentId;
    /** txn_ref | VARCHAR(50) | NOT NULL */
    private String txnRef;
    /** gateway_txn_no | VARCHAR(50) */
    private String gatewayTxnNo;
    /** bank_code | VARCHAR(20) */
    private String bankCode;
    /** response_code | VARCHAR(5) */
    private String responseCode;
    /** pay_date | TIMESTAMPTZ */
    private OffsetDateTime payDate;

    public PaymentGatewayTransaction() {}

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public String getTxnRef() { return txnRef; }
    public void setTxnRef(String txnRef) { this.txnRef = txnRef; }
    public String getGatewayTxnNo() { return gatewayTxnNo; }
    public void setGatewayTxnNo(String gatewayTxnNo) { this.gatewayTxnNo = gatewayTxnNo; }
    public String getBankCode() { return bankCode; }
    public void setBankCode(String bankCode) { this.bankCode = bankCode; }
    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }
    public OffsetDateTime getPayDate() { return payDate; }
    public void setPayDate(OffsetDateTime payDate) { this.payDate = payDate; }

    @Override
    public String toString() {
        return "PaymentGatewayTransaction{" + "paymentId=" + paymentId + ", txnRef=" + txnRef + ", gatewayTxnNo=" + gatewayTxnNo + ", bankCode=" + bankCode + ", responseCode=" + responseCode + ", payDate=" + payDate + "}";
    }
}
