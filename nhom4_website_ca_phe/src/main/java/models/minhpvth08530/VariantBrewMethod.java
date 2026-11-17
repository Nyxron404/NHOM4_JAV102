package models.minhpvth08530;

/**
 * Gợi ý pha cho biến thể (N-N)
 * <p>Bảng: variant_brew_methods</p>
 */
public class VariantBrewMethod {

    public static final String TABLE = "variant_brew_methods";
    public static final String[] PRIMARY_KEY = {"variant_id", "brew_method_id"};

    private Integer variantId;
    private Integer brewMethodId;
    private String note;

    // Thông tin hiển thị (JOIN brew_methods, không lưu DB)
    private String methodName;
    private String coffeeWaterRatio;
    private String waterTempC;
    private String brewTime;

    public VariantBrewMethod() {}

    public Integer getVariantId() { return variantId; }
    public void setVariantId(Integer variantId) { this.variantId = variantId; }
    public Integer getBrewMethodId() { return brewMethodId; }
    public void setBrewMethodId(Integer brewMethodId) { this.brewMethodId = brewMethodId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public String getCoffeeWaterRatio() { return coffeeWaterRatio; }
    public void setCoffeeWaterRatio(String coffeeWaterRatio) { this.coffeeWaterRatio = coffeeWaterRatio; }
    public String getWaterTempC() { return waterTempC; }
    public void setWaterTempC(String waterTempC) { this.waterTempC = waterTempC; }
    public String getBrewTime() { return brewTime; }
    public void setBrewTime(String brewTime) { this.brewTime = brewTime; }

    @Override
    public String toString() {
        return "VariantBrewMethod{" + "variantId=" + variantId + ", brewMethodId=" + brewMethodId + ", note=" + note + "}";
    }
}
