package models.minhpvth08530;

/**
 * Một lựa chọn trong danh mục (vùng trồng, giống, sơ chế, mức rang, cách pha, độ xay, nốt hương)
 * dùng cho dropdown ở trang quản lý sản phẩm. Chỉ đọc.
 */
public class LookupItem {

    private Integer id;
    private String name;
    /** Thông tin phụ: tỉnh của vùng trồng, loài của giống, nhóm nốt hương... */
    private String detail;

    public LookupItem() {}

    public LookupItem(Integer id, String name, String detail) {
        this.id = id;
        this.name = name;
        this.detail = detail;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    @Override
    public String toString() {
        return "LookupItem{" + "id=" + id + ", name=" + name + ", detail=" + detail + "}";
    }
}
