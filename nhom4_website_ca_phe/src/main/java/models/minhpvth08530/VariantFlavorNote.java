package models.minhpvth08530;

/**
 * Nốt hương của biến thể (N-N)
 * <p>Bảng: variant_flavor_notes</p>
 */
public class VariantFlavorNote {

    public static final String TABLE = "variant_flavor_notes";
    public static final String[] PRIMARY_KEY = {"variant_id", "flavor_note_id"};

    private Integer variantId;
    private Integer flavorNoteId;

    public VariantFlavorNote() {}

    public Integer getVariantId() { return variantId; }
    public void setVariantId(Integer variantId) { this.variantId = variantId; }
    public Integer getFlavorNoteId() { return flavorNoteId; }
    public void setFlavorNoteId(Integer flavorNoteId) { this.flavorNoteId = flavorNoteId; }

    @Override
    public String toString() {
        return "VariantFlavorNote{" + "variantId=" + variantId + ", flavorNoteId=" + flavorNoteId + "}";
    }
}
