package models.product;

import java.time.OffsetDateTime;

import models.enums.VariantStatus;

/**
 * Biến thể = tổ hợp vùng, giống, sơ chế, mức rang
 * <p>Bảng: product_variants</p>
 */
public class ProductVariant {

    public static final String TABLE = "product_variants";
    public static final String[] PRIMARY_KEY = {"variant_id"};

    /** variant_id | INT | PK | tự tăng */
    private Integer variantId;
    /** product_id | INT | FK → products.product_id | NOT NULL */
    private Integer productId;
    /** variant_code | d_code | NOT NULL */
    private String variantCode;
    /** variant_name | d_name | NOT NULL */
    private String variantName;
    /** region_id | INT | FK → growing_regions.region_id */
    private Integer regionId;
    /** variety_id | INT | FK → coffee_varieties.variety_id */
    private Integer varietyId;
    /** process_id | INT | FK → processing_methods.process_id */
    private Integer processId;
    /** roast_level_id | INT | FK → roast_levels.roast_level_id */
    private Integer roastLevelId;
    /** altitude_m | INT */
    private Integer altitudeM;
    /** harvest_season | VARCHAR(20) */
    private String harvestSeason;
    /** aroma_score | SMALLINT */
    private Integer aromaScore;
    /** acidity_score | SMALLINT */
    private Integer acidityScore;
    /** sweetness_score | SMALLINT */
    private Integer sweetnessScore;
    /** bitterness_score | SMALLINT */
    private Integer bitternessScore;
    /** body_score | SMALLINT */
    private Integer bodyScore;
    /** aftertaste_score | SMALLINT */
    private Integer aftertasteScore;
    /** display_order | SMALLINT | NOT NULL */
    private Integer displayOrder;
    /** status | VARCHAR(10) | NOT NULL */
    private VariantStatus status = VariantStatus.DRAFT;
    /** created_at | TIMESTAMPTZ | NOT NULL */
    private OffsetDateTime createdAt;

    public ProductVariant() {}

    public Integer getVariantId() { return variantId; }
    public void setVariantId(Integer variantId) { this.variantId = variantId; }
    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }
    public String getVariantCode() { return variantCode; }
    public void setVariantCode(String variantCode) { this.variantCode = variantCode; }
    public String getVariantName() { return variantName; }
    public void setVariantName(String variantName) { this.variantName = variantName; }
    public Integer getRegionId() { return regionId; }
    public void setRegionId(Integer regionId) { this.regionId = regionId; }
    public Integer getVarietyId() { return varietyId; }
    public void setVarietyId(Integer varietyId) { this.varietyId = varietyId; }
    public Integer getProcessId() { return processId; }
    public void setProcessId(Integer processId) { this.processId = processId; }
    public Integer getRoastLevelId() { return roastLevelId; }
    public void setRoastLevelId(Integer roastLevelId) { this.roastLevelId = roastLevelId; }
    public Integer getAltitudeM() { return altitudeM; }
    public void setAltitudeM(Integer altitudeM) { this.altitudeM = altitudeM; }
    public String getHarvestSeason() { return harvestSeason; }
    public void setHarvestSeason(String harvestSeason) { this.harvestSeason = harvestSeason; }
    public Integer getAromaScore() { return aromaScore; }
    public void setAromaScore(Integer aromaScore) { this.aromaScore = aromaScore; }
    public Integer getAcidityScore() { return acidityScore; }
    public void setAcidityScore(Integer acidityScore) { this.acidityScore = acidityScore; }
    public Integer getSweetnessScore() { return sweetnessScore; }
    public void setSweetnessScore(Integer sweetnessScore) { this.sweetnessScore = sweetnessScore; }
    public Integer getBitternessScore() { return bitternessScore; }
    public void setBitternessScore(Integer bitternessScore) { this.bitternessScore = bitternessScore; }
    public Integer getBodyScore() { return bodyScore; }
    public void setBodyScore(Integer bodyScore) { this.bodyScore = bodyScore; }
    public Integer getAftertasteScore() { return aftertasteScore; }
    public void setAftertasteScore(Integer aftertasteScore) { this.aftertasteScore = aftertasteScore; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    public VariantStatus getStatus() { return status; }
    public void setStatus(VariantStatus status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "ProductVariant{" + "variantId=" + variantId + ", productId=" + productId + ", variantCode=" + variantCode + ", variantName=" + variantName + ", regionId=" + regionId + ", varietyId=" + varietyId + ", processId=" + processId + ", roastLevelId=" + roastLevelId + ", altitudeM=" + altitudeM + ", harvestSeason=" + harvestSeason + ", aromaScore=" + aromaScore + ", acidityScore=" + acidityScore + ", sweetnessScore=" + sweetnessScore + ", bitternessScore=" + bitternessScore + ", bodyScore=" + bodyScore + ", aftertasteScore=" + aftertasteScore + ", displayOrder=" + displayOrder + ", status=" + status + ", createdAt=" + createdAt + "}";
    }
}
