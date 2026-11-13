package models.sales;

/**
 * Phương thức thanh toán (Tiền mặt, VNPay...)
 * <p>Bảng: payment_methods</p>
 */
public class PaymentMethod {

    public static final String TABLE = "payment_methods";
    public static final String[] PRIMARY_KEY = {"payment_method_id"};

    /** payment_method_id | INT | PK | tự tăng */
    private Integer paymentMethodId;
    /** method_code | d_code | NOT NULL */
    private String methodCode;
    /** method_name | d_name | NOT NULL */
    private String methodName;
    /** is_online | BOOLEAN | NOT NULL */
    private Boolean isOnline;
    /** is_active | BOOLEAN | NOT NULL */
    private Boolean isActive;

    public PaymentMethod() {}

    public Integer getPaymentMethodId() { return paymentMethodId; }
    public void setPaymentMethodId(Integer paymentMethodId) { this.paymentMethodId = paymentMethodId; }
    public String getMethodCode() { return methodCode; }
    public void setMethodCode(String methodCode) { this.methodCode = methodCode; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public Boolean getIsOnline() { return isOnline; }
    public void setIsOnline(Boolean isOnline) { this.isOnline = isOnline; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    @Override
    public String toString() {
        return "PaymentMethod{" + "paymentMethodId=" + paymentMethodId + ", methodCode=" + methodCode + ", methodName=" + methodName + ", isOnline=" + isOnline + ", isActive=" + isActive + "}";
    }
}
