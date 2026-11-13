package models.catalog;

/**
 * Giống cà phê
 * <p>Bảng: coffee_varieties</p>
 */
public class CoffeeVariety {

    public static final String TABLE = "coffee_varieties";
    public static final String[] PRIMARY_KEY = {"variety_id"};

    /** variety_id | INT | PK | tự tăng */
    private Integer varietyId;
    /** species_id | INT | FK → coffee_species.species_id | NOT NULL */
    private Integer speciesId;
    /** variety_name | d_name | NOT NULL */
    private String varietyName;
    /** description | d_text */
    private String description;

    public CoffeeVariety() {}

    public Integer getVarietyId() { return varietyId; }
    public void setVarietyId(Integer varietyId) { this.varietyId = varietyId; }
    public Integer getSpeciesId() { return speciesId; }
    public void setSpeciesId(Integer speciesId) { this.speciesId = speciesId; }
    public String getVarietyName() { return varietyName; }
    public void setVarietyName(String varietyName) { this.varietyName = varietyName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return "CoffeeVariety{" + "varietyId=" + varietyId + ", speciesId=" + speciesId + ", varietyName=" + varietyName + ", description=" + description + "}";
    }
}
