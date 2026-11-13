package models.hieulnmth084701;

/**
 * Tỉnh/thành
 * <p>Bảng: provinces</p>
 */
public class Province {

    public static final String TABLE = "provinces";
    public static final String[] PRIMARY_KEY = {"province_id"};

    /** province_id | INT | PK | tự tăng */
    private Integer provinceId;
    /** province_name | d_name | NOT NULL */
    private String provinceName;

    public Province() {}

    public Integer getProvinceId() { return provinceId; }
    public void setProvinceId(Integer provinceId) { this.provinceId = provinceId; }
    public String getProvinceName() { return provinceName; }
    public void setProvinceName(String provinceName) { this.provinceName = provinceName; }

    @Override
    public String toString() {
        return "Province{" + "provinceId=" + provinceId + ", provinceName=" + provinceName + "}";
    }
}
