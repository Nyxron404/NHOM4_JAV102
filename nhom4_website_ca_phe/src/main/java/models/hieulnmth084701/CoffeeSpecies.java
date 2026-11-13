package models.hieulnmth084701;

/**
 * Loài cà phê (Arabica, Robusta, Liberica, Excelsa)
 * <p>Bảng: coffee_species</p>
 */
public class CoffeeSpecies {

    public static final String TABLE = "coffee_species";
    public static final String[] PRIMARY_KEY = {"species_id"};

    /** species_id | INT | PK | tự tăng */
    private Integer speciesId;
    /** species_name | d_name | NOT NULL */
    private String speciesName;
    /** description | d_text */
    private String description;

    public CoffeeSpecies() {}

    public Integer getSpeciesId() { return speciesId; }
    public void setSpeciesId(Integer speciesId) { this.speciesId = speciesId; }
    public String getSpeciesName() { return speciesName; }
    public void setSpeciesName(String speciesName) { this.speciesName = speciesName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return "CoffeeSpecies{" + "speciesId=" + speciesId + ", speciesName=" + speciesName + ", description=" + description + "}";
    }
}
