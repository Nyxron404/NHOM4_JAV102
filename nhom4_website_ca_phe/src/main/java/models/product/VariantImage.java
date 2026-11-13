package models.product;

/**
 * Ảnh biến thể (thuộc tính đa trị)
 * <p>Bảng: variant_images</p>
 */
public class VariantImage {

    public static final String TABLE = "variant_images";
    public static final String[] PRIMARY_KEY = {"image_id"};

    /** image_id | INT | PK | tự tăng */
    private Integer imageId;
    /** variant_id | INT | FK → product_variants.variant_id | NOT NULL */
    private Integer variantId;
    /** image_url | VARCHAR(500) | NOT NULL */
    private String imageUrl;
    /** sort_order | SMALLINT | NOT NULL */
    private Integer sortOrder;

    public VariantImage() {}

    public Integer getImageId() { return imageId; }
    public void setImageId(Integer imageId) { this.imageId = imageId; }
    public Integer getVariantId() { return variantId; }
    public void setVariantId(Integer variantId) { this.variantId = variantId; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String toString() {
        return "VariantImage{" + "imageId=" + imageId + ", variantId=" + variantId + ", imageUrl=" + imageUrl + ", sortOrder=" + sortOrder + "}";
    }
}
