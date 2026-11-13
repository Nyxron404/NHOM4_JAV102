package models.hieuttth082971;

/**
 * Tồn theo gói bán + cờ tồn thấp (dữ liệu dẫn xuất, không INSERT/UPDATE)
 * <p>View (chỉ đọc): v_package_stock</p>
 */
public class PackageStock {

    public static final String VIEW = "v_package_stock";

    /** package_id | INT */
    private Integer packageId;
    /** sku | d_code */
    private String sku;
    /** min_stock_level | INT */
    private Integer minStockLevel;
    /** quantity_on_hand | BIGINT | SUM của SUM */
    private Long quantityOnHand;
    /** is_low_stock | BOOLEAN */
    private Boolean isLowStock;

    public PackageStock() {}

    public Integer getPackageId() { return packageId; }
    public void setPackageId(Integer packageId) { this.packageId = packageId; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public Integer getMinStockLevel() { return minStockLevel; }
    public void setMinStockLevel(Integer minStockLevel) { this.minStockLevel = minStockLevel; }
    public Long getQuantityOnHand() { return quantityOnHand; }
    public void setQuantityOnHand(Long quantityOnHand) { this.quantityOnHand = quantityOnHand; }
    public Boolean getIsLowStock() { return isLowStock; }
    public void setIsLowStock(Boolean isLowStock) { this.isLowStock = isLowStock; }

    @Override
    public String toString() {
        return "PackageStock{" + "packageId=" + packageId + ", sku=" + sku + ", minStockLevel=" + minStockLevel + ", quantityOnHand=" + quantityOnHand + ", isLowStock=" + isLowStock + "}";
    }
}
