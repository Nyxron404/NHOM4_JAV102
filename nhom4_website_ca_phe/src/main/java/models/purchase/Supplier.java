package models.purchase;

import models.enums.ActiveStatus;

/**
 * Nhà cung cấp
 * <p>Bảng: suppliers</p>
 */
public class Supplier {

    public static final String TABLE = "suppliers";
    public static final String[] PRIMARY_KEY = {"supplier_id"};

    /** supplier_id | INT | PK | tự tăng */
    private Integer supplierId;
    /** supplier_code | d_code | NOT NULL */
    private String supplierCode;
    /** supplier_name | d_name | NOT NULL */
    private String supplierName;
    /** tax_code | d_tax_code */
    private String taxCode;
    /** address | d_address */
    private String address;
    /** phone | d_phone */
    private String phone;
    /** email | d_email */
    private String email;
    /** status | VARCHAR(10) | NOT NULL */
    private ActiveStatus status = ActiveStatus.ACTIVE;

    public Supplier() {}

    public Integer getSupplierId() { return supplierId; }
    public void setSupplierId(Integer supplierId) { this.supplierId = supplierId; }
    public String getSupplierCode() { return supplierCode; }
    public void setSupplierCode(String supplierCode) { this.supplierCode = supplierCode; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getTaxCode() { return taxCode; }
    public void setTaxCode(String taxCode) { this.taxCode = taxCode; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public ActiveStatus getStatus() { return status; }
    public void setStatus(ActiveStatus status) { this.status = status; }

    @Override
    public String toString() {
        return "Supplier{" + "supplierId=" + supplierId + ", supplierCode=" + supplierCode + ", supplierName=" + supplierName + ", taxCode=" + taxCode + ", address=" + address + ", phone=" + phone + ", email=" + email + ", status=" + status + "}";
    }
}
