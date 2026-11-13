package models.minhphvth085302;

import models.enums.ActiveStatus;

/**
 * Gói bán (SKU): biến thể + trọng lượng + dạng hạt
 * <p>Bảng: packages</p>
 */
public class CoffeePackage {

    public static final String TABLE = "packages";
    public static final String[] PRIMARY_KEY = {"package_id"};

    /** package_id | INT | PK | tự tăng */
    private Integer packageId;
    /** variant_id | INT | FK → product_variants.variant_id | NOT NULL */
    private Integer variantId;
    /** grind_size_id | INT | FK → grind_sizes.grind_size_id | NOT NULL */
    private Integer grindSizeId;
    /** sku | d_code | NOT NULL */
    private String sku;
    /** weight_g | INT | NOT NULL */
    private Integer weightG;
    /** min_stock_level | INT | NOT NULL */
    private Integer minStockLevel;
    /** status | VARCHAR(10) | NOT NULL */
    private ActiveStatus status = ActiveStatus.ACTIVE;

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

    @Override
    public String toString() {
        return "CoffeePackage{" + "packageId=" + packageId + ", variantId=" + variantId + ", grindSizeId=" + grindSizeId + ", sku=" + sku + ", weightG=" + weightG + ", minStockLevel=" + minStockLevel + ", status=" + status + "}";
    }
}
