package models.auth;

/**
 * Vai trò (CCH, NV quầy...)
 * <p>Bảng: roles</p>
 */
public class Role {

    public static final String TABLE = "roles";
    public static final String[] PRIMARY_KEY = {"role_id"};

    /** role_id | INT | PK | tự tăng */
    private Integer roleId;
    /** role_code | d_code | NOT NULL */
    private String roleCode;
    /** role_name | d_name | NOT NULL */
    private String roleName;
    /** description | d_text */
    private String description;

    public Role() {}

    public Integer getRoleId() { return roleId; }
    public void setRoleId(Integer roleId) { this.roleId = roleId; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return "Role{" + "roleId=" + roleId + ", roleCode=" + roleCode + ", roleName=" + roleName + ", description=" + description + "}";
    }
}
