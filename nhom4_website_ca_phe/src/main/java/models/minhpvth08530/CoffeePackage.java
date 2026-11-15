package models.minhpvth08530;

import models.enums.ActiveStatus;

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
