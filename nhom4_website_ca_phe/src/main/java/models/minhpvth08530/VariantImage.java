package models.minhpvth08530;

/**
 * Ảnh biến thể (thuộc tính đa trị)
 * <p>Bảng: variant_images</p>
 */
public class VariantImage {

    public static final String TABLE = "variant_images";
    public static final String[] PRIMARY_KEY = {"image_id"};

    private Integer imageId;
    private Integer variantId;
    private String imageUrl;
    private String publicId;
    private Integer sortOrder;

    public VariantImage() {}

    public Integer getImageId() { return imageId; }
    public void setImageId(Integer imageId) { this.imageId = imageId; }
    public Integer getVariantId() { return variantId; }
    public void setVariantId(Integer variantId) { this.variantId = variantId; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String toString() {
        return "VariantImage{" + "imageId=" + imageId + ", variantId=" + variantId + ", imageUrl=" + imageUrl + ", publicId=" + publicId + ", sortOrder=" + sortOrder + "}";
    }
}
