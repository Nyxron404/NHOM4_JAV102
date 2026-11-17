package models.minhpvth08530;

import java.time.OffsetDateTime;

import models.enums.ActiveStatus;

/**
 * Sản phẩm (trang QR chung, vd Cafe Robusta)
 * <p>Bảng: products</p>
 */
public class Product {

    public static final String TABLE = "products";
    public static final String[] PRIMARY_KEY = {"product_id"};

    private Integer productId;
    private String productCode;
    private String productName;
    private String description;
    private String qrToken;
    private ActiveStatus status = ActiveStatus.ACTIVE;
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
