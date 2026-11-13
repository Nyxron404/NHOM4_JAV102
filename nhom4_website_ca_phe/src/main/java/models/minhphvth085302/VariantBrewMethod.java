package models.minhphvth085302;

/**
 * Gợi ý pha cho biến thể (N-N)
 * <p>Bảng: variant_brew_methods</p>
 */
public class VariantBrewMethod {

    public static final String TABLE = "variant_brew_methods";
    public static final String[] PRIMARY_KEY = {"variant_id", "brew_method_id"};

    /** variant_id | INT | PK | FK → product_variants.variant_id */
    private Integer variantId;
    /** brew_method_id | INT | PK | FK → brew_methods.brew_method_id */
    private Integer brewMethodId;
    /** note | d_note */
    private String note;

    public VariantBrewMethod() {}

    public Integer getVariantId() { return variantId; }
    public void setVariantId(Integer variantId) { this.variantId = variantId; }
    public Integer getBrewMethodId() { return brewMethodId; }
    public void setBrewMethodId(Integer brewMethodId) { this.brewMethodId = brewMethodId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    @Override
    public String toString() {
        return "VariantBrewMethod{" + "variantId=" + variantId + ", brewMethodId=" + brewMethodId + ", note=" + note + "}";
    }
}
