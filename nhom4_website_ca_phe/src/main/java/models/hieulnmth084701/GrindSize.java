package models.hieulnmth084701;

/**
 * Độ xay / dạng sản phẩm (gồm cả Nguyên hạt)
 * <p>Bảng: grind_sizes</p>
 */
public class GrindSize {

    public static final String TABLE = "grind_sizes";
    public static final String[] PRIMARY_KEY = {"grind_size_id"};

    /** grind_size_id | INT | PK | tự tăng */
    private Integer grindSizeId;
    /** grind_code | d_code | NOT NULL */
    private String grindCode;
    /** grind_name | d_name | NOT NULL */
    private String grindName;
    /** is_whole_bean | BOOLEAN | NOT NULL */
    private Boolean isWholeBean;
    /** sort_order | SMALLINT | NOT NULL */
    private Integer sortOrder;
    /** description | d_text */
    private String description;

    public GrindSize() {}

    public Integer getGrindSizeId() { return grindSizeId; }
    public void setGrindSizeId(Integer grindSizeId) { this.grindSizeId = grindSizeId; }
    public String getGrindCode() { return grindCode; }
    public void setGrindCode(String grindCode) { this.grindCode = grindCode; }
    public String getGrindName() { return grindName; }
    public void setGrindName(String grindName) { this.grindName = grindName; }
    public Boolean getIsWholeBean() { return isWholeBean; }
    public void setIsWholeBean(Boolean isWholeBean) { this.isWholeBean = isWholeBean; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return "GrindSize{" + "grindSizeId=" + grindSizeId + ", grindCode=" + grindCode + ", grindName=" + grindName + ", isWholeBean=" + isWholeBean + ", sortOrder=" + sortOrder + ", description=" + description + "}";
    }
}
