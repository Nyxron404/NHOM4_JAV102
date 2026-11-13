package models.product;

import java.time.OffsetDateTime;

import models.enums.ActiveStatus;

/**
 * Sản phẩm (trang QR chung, vd Cafe Robusta)
 * <p>Bảng: products</p>
 */
public class Product {

    public static final String TABLE = "products";
    public static final String[] PRIMARY_KEY = {"product_id"};

    /** product_id | INT | PK | tự tăng */
    private Integer productId;
    /** product_code | d_code | NOT NULL */
    private String productCode;
    /** product_name | d_name | NOT NULL */
    private String productName;
    /** description | d_text */
    private String description;
    /** qr_token | VARCHAR(64) | NOT NULL */
    private String qrToken;
    /** status | VARCHAR(12) | NOT NULL */
    private ActiveStatus status = ActiveStatus.ACTIVE;
    /** created_at | TIMESTAMPTZ | NOT NULL */
    private OffsetDateTime createdAt;

    public Product() {}

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getQrToken() { return qrToken; }
    public void setQrToken(String qrToken) { this.qrToken = qrToken; }
    public ActiveStatus getStatus() { return status; }
    public void setStatus(ActiveStatus status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Product{" + "productId=" + productId + ", productCode=" + productCode + ", productName=" + productName + ", description=" + description + ", qrToken=" + qrToken + ", status=" + status + ", createdAt=" + createdAt + "}";
    }
}
