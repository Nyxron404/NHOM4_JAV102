package models.auth;

/**
 * Quyền chức năng
 * <p>Bảng: permissions</p>
 */
public class Permission {

    public static final String TABLE = "permissions";
    public static final String[] PRIMARY_KEY = {"permission_id"};

    /** permission_id | INT | PK | tự tăng */
    private Integer permissionId;
    /** permission_code | d_code | NOT NULL */
    private String permissionCode;
    /** permission_name | d_name | NOT NULL */
    private String permissionName;
    /** module | VARCHAR(40) | NOT NULL */
    private String module;

    public Permission() {}

    public Integer getPermissionId() { return permissionId; }
    public void setPermissionId(Integer permissionId) { this.permissionId = permissionId; }
    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }
    public String getPermissionName() { return permissionName; }
    public void setPermissionName(String permissionName) { this.permissionName = permissionName; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    @Override
    public String toString() {
        return "Permission{" + "permissionId=" + permissionId + ", permissionCode=" + permissionCode + ", permissionName=" + permissionName + ", module=" + module + "}";
    }
}
