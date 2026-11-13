package models.hieulnmth084701;

/**
 * Vùng trồng (Cầu Đất, Khe Sanh...)
 * <p>Bảng: growing_regions</p>
 */
public class GrowingRegion {

    public static final String TABLE = "growing_regions";
    public static final String[] PRIMARY_KEY = {"region_id"};

    /** region_id | INT | PK | tự tăng */
    private Integer regionId;
    /** province_id | INT | FK → provinces.province_id | NOT NULL */
    private Integer provinceId;
    /** region_name | d_name | NOT NULL */
    private String regionName;
    /** altitude_min_m | INT */
    private Integer altitudeMinM;
    /** altitude_max_m | INT */
    private Integer altitudeMaxM;
    /** soil | d_text */
    private String soil;
    /** climate | d_text */
    private String climate;
    /** description | d_text */
    private String description;

    public GrowingRegion() {}

    public Integer getRegionId() { return regionId; }
    public void setRegionId(Integer regionId) { this.regionId = regionId; }
    public Integer getProvinceId() { return provinceId; }
    public void setProvinceId(Integer provinceId) { this.provinceId = provinceId; }
    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }
    public Integer getAltitudeMinM() { return altitudeMinM; }
    public void setAltitudeMinM(Integer altitudeMinM) { this.altitudeMinM = altitudeMinM; }
    public Integer getAltitudeMaxM() { return altitudeMaxM; }
    public void setAltitudeMaxM(Integer altitudeMaxM) { this.altitudeMaxM = altitudeMaxM; }
    public String getSoil() { return soil; }
    public void setSoil(String soil) { this.soil = soil; }
    public String getClimate() { return climate; }
    public void setClimate(String climate) { this.climate = climate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return "GrowingRegion{" + "regionId=" + regionId + ", provinceId=" + provinceId + ", regionName=" + regionName + ", altitudeMinM=" + altitudeMinM + ", altitudeMaxM=" + altitudeMaxM + ", soil=" + soil + ", climate=" + climate + ", description=" + description + "}";
    }
}
