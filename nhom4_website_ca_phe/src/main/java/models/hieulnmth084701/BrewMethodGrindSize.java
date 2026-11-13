package models.hieulnmth084701;

/**
 * Độ xay phù hợp cho từng phương pháp pha (N-N)
 * <p>Bảng: brew_method_grind_sizes</p>
 */
public class BrewMethodGrindSize {

    public static final String TABLE = "brew_method_grind_sizes";
    public static final String[] PRIMARY_KEY = {"brew_method_id", "grind_size_id"};

    /** brew_method_id | INT | PK | FK → brew_methods.brew_method_id */
    private Integer brewMethodId;
    /** grind_size_id | INT | PK | FK → grind_sizes.grind_size_id */
    private Integer grindSizeId;
    /** is_recommended | BOOLEAN | NOT NULL */
    private Boolean isRecommended;

    public BrewMethodGrindSize() {}

    public Integer getBrewMethodId() { return brewMethodId; }
    public void setBrewMethodId(Integer brewMethodId) { this.brewMethodId = brewMethodId; }
    public Integer getGrindSizeId() { return grindSizeId; }
    public void setGrindSizeId(Integer grindSizeId) { this.grindSizeId = grindSizeId; }
    public Boolean getIsRecommended() { return isRecommended; }
    public void setIsRecommended(Boolean isRecommended) { this.isRecommended = isRecommended; }

    @Override
    public String toString() {
        return "BrewMethodGrindSize{" + "brewMethodId=" + brewMethodId + ", grindSizeId=" + grindSizeId + ", isRecommended=" + isRecommended + "}";
    }
}
