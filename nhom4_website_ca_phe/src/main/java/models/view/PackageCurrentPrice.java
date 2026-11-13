package models.view;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Giá bán hiện hành của gói bán (dữ liệu dẫn xuất, không INSERT/UPDATE)
 * <p>View (chỉ đọc): v_package_current_price</p>
 */
public class PackageCurrentPrice {

    public static final String VIEW = "v_package_current_price";

    /** package_id | INT */
    private Integer packageId;
    /** sale_price | NUMERIC(14,0) */
    private BigDecimal salePrice;
    /** effective_from | TIMESTAMPTZ */
    private OffsetDateTime effectiveFrom;

    public PackageCurrentPrice() {}

    public Integer getPackageId() { return packageId; }
    public void setPackageId(Integer packageId) { this.packageId = packageId; }
    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }
    public OffsetDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(OffsetDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    @Override
    public String toString() {
        return "PackageCurrentPrice{" + "packageId=" + packageId + ", salePrice=" + salePrice + ", effectiveFrom=" + effectiveFrom + "}";
    }
}
