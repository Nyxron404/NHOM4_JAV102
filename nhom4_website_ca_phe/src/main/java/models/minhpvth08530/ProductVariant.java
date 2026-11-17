package models.minhpvth08530;

import models.enums.VariantStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Biến thể = tổ hợp vùng, giống, sơ chế, mức rang
 * <p>Bảng: product_variants</p>
 */
public class ProductVariant {

    public static final String TABLE = "product_variants";
    public static final String[] PRIMARY_KEY = {"variant_id"};

    private Integer variantId;
    private Integer productId;
    private String variantCode;
    private String variantName;
    private Integer regionId;
    private Integer varietyId;
    private Integer processId;
    private Integer roastLevelId;
    private Integer altitudeM;
    private String harvestSeason;
    private Integer aromaScore;
    private Integer acidityScore;
    private Integer sweetnessScore;
    private Integer bitternessScore;
    private Integer bodyScore;
    private Integer aftertasteScore;
    private Integer displayOrder;
    private VariantStatus status = VariantStatus.DRAFT;
    private OffsetDateTime createdAt;

    // Thông tin hiển thị (tính từ JOIN, không lưu DB)
    private String productName;
    private String regionName;
    private String varietyName;
    private String processName;
    private String roastName;
    private Integer packageCount;
    private List<VariantBrewMethod> brewMethods = new ArrayList<>();
    private List<FlavorNote> flavorNotes = new ArrayList<>();
    private List<VariantImage> images = new ArrayList<>();

    public ProductVariant() {}

    /** Đủ 4 thuộc tính chuyên môn thì mới được ACTIVE (CHECK của bảng). */
    public boolean isComplete() {
        return regionId != null && varietyId != null && processId != null && roastLevelId != null;
    }

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
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }
    public String getVarietyName() { return varietyName; }
    public void setVarietyName(String varietyName) { this.varietyName = varietyName; }
    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
    public String getRoastName() { return roastName; }
    public void setRoastName(String roastName) { this.roastName = roastName; }
    public Integer getPackageCount() { return packageCount; }
    public void setPackageCount(Integer packageCount) { this.packageCount = packageCount; }
    public List<VariantBrewMethod> getBrewMethods() { return brewMethods; }
    public void setBrewMethods(List<VariantBrewMethod> brewMethods) { this.brewMethods = brewMethods; }
    public List<FlavorNote> getFlavorNotes() { return flavorNotes; }
    public void setFlavorNotes(List<FlavorNote> flavorNotes) { this.flavorNotes = flavorNotes; }
    public List<VariantImage> getImages() { return images; }
    public void setImages(List<VariantImage> images) { this.images = images; }

    @Override
    public String toString() {
        return "ProductVariant{" + "variantId=" + variantId + ", productId=" + productId + ", variantCode=" + variantCode + ", variantName=" + variantName + ", regionId=" + regionId + ", varietyId=" + varietyId + ", processId=" + processId + ", roastLevelId=" + roastLevelId + ", altitudeM=" + altitudeM + ", harvestSeason=" + harvestSeason + ", aromaScore=" + aromaScore + ", acidityScore=" + acidityScore + ", sweetnessScore=" + sweetnessScore + ", bitternessScore=" + bitternessScore + ", bodyScore=" + bodyScore + ", aftertasteScore=" + aftertasteScore + ", displayOrder=" + displayOrder + ", status=" + status + ", createdAt=" + createdAt + "}";
    }
}
