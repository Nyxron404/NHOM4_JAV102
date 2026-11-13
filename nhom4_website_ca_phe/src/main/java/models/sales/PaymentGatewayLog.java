package models.sales;

import java.time.OffsetDateTime;

import models.enums.GatewayLogSource;

/**
 * Log thô IPN/Return từ VNPay (đối soát)
 * <p>Bảng: payment_gateway_logs</p>
 */
public class PaymentGatewayLog {

    public static final String TABLE = "payment_gateway_logs";
    public static final String[] PRIMARY_KEY = {"log_id"};

    /** log_id | BIGINT | PK | tự tăng */
    private Long logId;
    /** payment_id | BIGINT | FK → payments.payment_id */
    private Long paymentId;
    /** source | VARCHAR(10) | NOT NULL */
    private GatewayLogSource source;
    /** payload | JSONB | NOT NULL */
    private String payload;
    /** signature_valid | BOOLEAN | NOT NULL */
    private Boolean signatureValid;
    /** received_at | TIMESTAMPTZ | NOT NULL */
    private OffsetDateTime receivedAt;

    public PaymentGatewayLog() {}

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public GatewayLogSource getSource() { return source; }
    public void setSource(GatewayLogSource source) { this.source = source; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Boolean getSignatureValid() { return signatureValid; }
    public void setSignatureValid(Boolean signatureValid) { this.signatureValid = signatureValid; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(OffsetDateTime receivedAt) { this.receivedAt = receivedAt; }

    @Override
    public String toString() {
        return "PaymentGatewayLog{" + "logId=" + logId + ", paymentId=" + paymentId + ", source=" + source + ", signatureValid=" + signatureValid + ", receivedAt=" + receivedAt + "}";
    }
}
