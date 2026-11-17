package models.minhpvth08530;

import models.enums.ActiveStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Gói bán (SKU): biến thể + trọng lượng + dạng hạt
 * <p>Bảng: packages</p>
 */
public class CoffeePackage {

    public static final String TABLE = "packages";
    public static final String[] PRIMARY_KEY = {"package_id"};

    private Integer packageId;
    private Integer variantId;
    private Integer grindSizeId;
    private String sku;
    private Integer weightG;
    private Integer minStockLevel;
    private ActiveStatus status = ActiveStatus.ACTIVE;

    // Thông tin hiển thị (JOIN + view v_package_current_price, v_package_stock; không lưu DB)
    private String variantName;
    private String variantStatus;
    private Integer productId;
    private String productName;
    private String grindName;
    private Boolean wholeBean;
    private BigDecimal salePrice;
    private OffsetDateTime priceEffectiveFrom;
    private Integer quantityOnHand;
    private Boolean lowStock;

    public CoffeePackage() {}

    public Integer getPackageId() { return packageId; }
    public void setPackageId(Integer packageId) { this.packageId = packageId; }
    public Integer getVariantId() { return variantId; }
    public void setVariantId(Integer variantId) { this.variantId = variantId; }
    public Integer getGrindSizeId() { return grindSizeId; }
    public void setGrindSizeId(Integer grindSizeId) { this.grindSizeId = grindSizeId; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public Integer getWeightG() { return weightG; }
    public void setWeightG(Integer weightG) { this.weightG = weightG; }
    public Integer getMinStockLevel() { return minStockLevel; }
    public void setMinStockLevel(Integer minStockLevel) { this.minStockLevel = minStockLevel; }
    public ActiveStatus getStatus() { return status; }
    public void setStatus(ActiveStatus status) { this.status = status; }
    public String getVariantName() { return variantName; }
    public void setVariantName(String variantName) { this.variantName = variantName; }
    public String getVariantStatus() { return variantStatus; }
    public void setVariantStatus(String variantStatus) { this.variantStatus = variantStatus; }
    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getGrindName() { return grindName; }
    public void setGrindName(String grindName) { this.grindName = grindName; }
    public Boolean getWholeBean() { return wholeBean; }
    public void setWholeBean(Boolean wholeBean) { this.wholeBean = wholeBean; }
    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }
    public OffsetDateTime getPriceEffectiveFrom() { return priceEffectiveFrom; }
    public void setPriceEffectiveFrom(OffsetDateTime priceEffectiveFrom) { this.priceEffectiveFrom = priceEffectiveFrom; }
    public Integer getQuantityOnHand() { return quantityOnHand; }
    public void setQuantityOnHand(Integer quantityOnHand) { this.quantityOnHand = quantityOnHand; }
    public Boolean getLowStock() { return lowStock; }
    public void setLowStock(Boolean lowStock) { this.lowStock = lowStock; }

    @Override
    public String toString() {
        return "CoffeePackage{" + "packageId=" + packageId + ", variantId=" + variantId + ", grindSizeId=" + grindSizeId + ", sku=" + sku + ", weightG=" + weightG + ", minStockLevel=" + minStockLevel + ", status=" + status + "}";
    }
}
