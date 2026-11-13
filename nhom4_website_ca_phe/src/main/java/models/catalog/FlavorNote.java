package models.catalog;

/**
 * Nốt hương (nhài, cam, socola...)
 * <p>Bảng: flavor_notes</p>
 */
public class FlavorNote {

    public static final String TABLE = "flavor_notes";
    public static final String[] PRIMARY_KEY = {"flavor_note_id"};

    /** flavor_note_id | INT | PK | tự tăng */
    private Integer flavorNoteId;
    /** note_name | d_name | NOT NULL */
    private String noteName;
    /** category | VARCHAR(30) | NOT NULL */
    private String category;

    public FlavorNote() {}

    public Integer getFlavorNoteId() { return flavorNoteId; }
    public void setFlavorNoteId(Integer flavorNoteId) { this.flavorNoteId = flavorNoteId; }
    public String getNoteName() { return noteName; }
    public void setNoteName(String noteName) { this.noteName = noteName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    @Override
    public String toString() {
        return "FlavorNote{" + "flavorNoteId=" + flavorNoteId + ", noteName=" + noteName + ", category=" + category + "}";
    }
}
