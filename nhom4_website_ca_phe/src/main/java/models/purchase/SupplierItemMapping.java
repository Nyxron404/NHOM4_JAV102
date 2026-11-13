package models.purchase;

/**
 * Ánh xạ mã hàng NCC -> gói bán (nhớ cho lần nhập sau)
 * <p>Bảng: supplier_item_mappings</p>
 */
public class SupplierItemMapping {

    public static final String TABLE = "supplier_item_mappings";
    public static final String[] PRIMARY_KEY = {"mapping_id"};

    /** mapping_id | INT | PK | tự tăng */
    private Integer mappingId;
    /** supplier_id | INT | FK → suppliers.supplier_id | NOT NULL */
    private Integer supplierId;
    /** supplier_item_code | VARCHAR(50) | NOT NULL */
    private String supplierItemCode;
    /** package_id | INT | FK → packages.package_id | NOT NULL */
    private Integer packageId;

    public SupplierItemMapping() {}

    public Integer getMappingId() { return mappingId; }
    public void setMappingId(Integer mappingId) { this.mappingId = mappingId; }
    public Integer getSupplierId() { return supplierId; }
    public void setSupplierId(Integer supplierId) { this.supplierId = supplierId; }
    public String getSupplierItemCode() { return supplierItemCode; }
    public void setSupplierItemCode(String supplierItemCode) { this.supplierItemCode = supplierItemCode; }
    public Integer getPackageId() { return packageId; }
    public void setPackageId(Integer packageId) { this.packageId = packageId; }

    @Override
    public String toString() {
        return "SupplierItemMapping{" + "mappingId=" + mappingId + ", supplierId=" + supplierId + ", supplierItemCode=" + supplierItemCode + ", packageId=" + packageId + "}";
    }
}
