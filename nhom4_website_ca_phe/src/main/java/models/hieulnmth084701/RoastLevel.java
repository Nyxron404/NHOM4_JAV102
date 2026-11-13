package models.hieulnmth084701;

/**
 * Mức rang
 * <p>Bảng: roast_levels</p>
 */
public class RoastLevel {

    public static final String TABLE = "roast_levels";
    public static final String[] PRIMARY_KEY = {"roast_level_id"};

    /** roast_level_id | INT | PK | tự tăng */
    private Integer roastLevelId;
    /** roast_name | d_name | NOT NULL */
    private String roastName;
    /** sort_order | SMALLINT | NOT NULL */
    private Integer sortOrder;
    /** description | d_text */
    private String description;

    public RoastLevel() {}

    public Integer getRoastLevelId() { return roastLevelId; }
    public void setRoastLevelId(Integer roastLevelId) { this.roastLevelId = roastLevelId; }
    public String getRoastName() { return roastName; }
    public void setRoastName(String roastName) { this.roastName = roastName; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return "RoastLevel{" + "roastLevelId=" + roastLevelId + ", roastName=" + roastName + ", sortOrder=" + sortOrder + ", description=" + description + "}";
    }
}
