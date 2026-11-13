package models.catalog;

/**
 * Phương pháp pha
 * <p>Bảng: brew_methods</p>
 */
public class BrewMethod {

    public static final String TABLE = "brew_methods";
    public static final String[] PRIMARY_KEY = {"brew_method_id"};

    /** brew_method_id | INT | PK | tự tăng */
    private Integer brewMethodId;
    /** method_name | d_name | NOT NULL */
    private String methodName;
    /** coffee_water_ratio | VARCHAR(20) */
    private String coffeeWaterRatio;
    /** water_temp_c | VARCHAR(20) */
    private String waterTempC;
    /** brew_time | VARCHAR(30) */
    private String brewTime;
    /** guide | d_text */
    private String guide;

    public BrewMethod() {}

    public Integer getBrewMethodId() { return brewMethodId; }
    public void setBrewMethodId(Integer brewMethodId) { this.brewMethodId = brewMethodId; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public String getCoffeeWaterRatio() { return coffeeWaterRatio; }
    public void setCoffeeWaterRatio(String coffeeWaterRatio) { this.coffeeWaterRatio = coffeeWaterRatio; }
    public String getWaterTempC() { return waterTempC; }
    public void setWaterTempC(String waterTempC) { this.waterTempC = waterTempC; }
    public String getBrewTime() { return brewTime; }
    public void setBrewTime(String brewTime) { this.brewTime = brewTime; }
    public String getGuide() { return guide; }
    public void setGuide(String guide) { this.guide = guide; }

    @Override
    public String toString() {
        return "BrewMethod{" + "brewMethodId=" + brewMethodId + ", methodName=" + methodName + ", coffeeWaterRatio=" + coffeeWaterRatio + ", waterTempC=" + waterTempC + ", brewTime=" + brewTime + ", guide=" + guide + "}";
    }
}
