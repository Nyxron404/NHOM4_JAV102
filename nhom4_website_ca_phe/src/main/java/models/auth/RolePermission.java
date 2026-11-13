package models.auth;

/**
 * Gán quyền cho vai trò (N-N)
 * <p>Bảng: role_permissions</p>
 */
public class RolePermission {

    public static final String TABLE = "role_permissions";
    public static final String[] PRIMARY_KEY = {"role_id", "permission_id"};

    /** role_id | INT | PK | FK → roles.role_id */
    private Integer roleId;
    /** permission_id | INT | PK | FK → permissions.permission_id */
    private Integer permissionId;

    public RolePermission() {}

    public Integer getRoleId() { return roleId; }
    public void setRoleId(Integer roleId) { this.roleId = roleId; }
    public Integer getPermissionId() { return permissionId; }
    public void setPermissionId(Integer permissionId) { this.permissionId = permissionId; }

    @Override
    public String toString() {
        return "RolePermission{" + "roleId=" + roleId + ", permissionId=" + permissionId + "}";
    }
}
