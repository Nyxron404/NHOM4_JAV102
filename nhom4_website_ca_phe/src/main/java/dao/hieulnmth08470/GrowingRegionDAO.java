package dao.hieulnmth08470;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import models.hieulnmth08470.catalog.GrowingRegion;

/** Vùng trồng (bảng growing_regions) - UC 18-21. */
public class GrowingRegionDAO extends BaseDAO {

    private static final String COLUMNS =
            "r.region_id, r.province_id, r.region_name, r.altitude_min_m, r.altitude_max_m, "
            + "r.soil, r.climate, r.description";

    // 1. Xem danh sách (tìm theo tên không dấu, lọc theo tỉnh)
    public List<GrowingRegion> search(String keyword, Integer provinceId) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS
                + " FROM growing_regions r JOIN provinces p ON p.province_id = r.province_id WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (keyword != null) {
            sql.append(" AND ").append(searchCondition("r.region_name"));
            params.add(escapeLike(keyword));
        }
        if (provinceId != null) {
            sql.append(" AND r.province_id = ?");
            params.add(provinceId);
        }
        sql.append(" ORDER BY p.province_name, r.region_name");

        List<GrowingRegion> list = new ArrayList<>();
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    // 2. Tìm theo ID
    public GrowingRegion getById(int id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM growing_regions r WHERE r.region_id = ?";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // 3. Thêm mới - trả về region_id vừa tạo
    public int insert(GrowingRegion r) throws SQLException {
        String sql = "INSERT INTO growing_regions (province_id, region_name, altitude_min_m, altitude_max_m, "
                + "soil, climate, description) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING region_id";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            setValues(ps, r);
            return returningId(ps);
        }
    }

    // 4. Cập nhật
    public boolean update(GrowingRegion r) throws SQLException {
        String sql = "UPDATE growing_regions SET province_id = ?, region_name = ?, altitude_min_m = ?, "
                + "altitude_max_m = ?, soil = ?, climate = ?, description = ? WHERE region_id = ?";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            setValues(ps, r);
            ps.setInt(8, r.getRegionId());
            return ps.executeUpdate() > 0;
        }
    }

    // 5. Xóa (Servlet kiểm tra countUsage trước)
    public boolean delete(int id) throws SQLException {
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement("DELETE FROM growing_regions WHERE region_id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** Số biến thể sản phẩm đang dùng vùng trồng này (> 0 thì không được xóa). */
    public int countUsage(int id) throws SQLException {
        return count("SELECT count(*) FROM product_variants WHERE region_id = ?", id);
    }

    /** region_id -> số biến thể, hiển thị trên bảng danh sách. */
    public Map<Integer, Integer> usageMap() throws SQLException {
        return countMap("SELECT region_id, count(*) FROM product_variants "
                + "WHERE region_id IS NOT NULL GROUP BY region_id");
    }

    private void setValues(PreparedStatement ps, GrowingRegion r) throws SQLException {
        ps.setInt(1, r.getProvinceId());
        ps.setString(2, r.getRegionName());
        setInt(ps, 3, r.getAltitudeMinM());
        setInt(ps, 4, r.getAltitudeMaxM());
        setString(ps, 5, r.getSoil());
        setString(ps, 6, r.getClimate());
        setString(ps, 7, r.getDescription());
    }

    private GrowingRegion mapRow(ResultSet rs) throws SQLException {
        GrowingRegion r = new GrowingRegion();
        r.setRegionId(rs.getInt("region_id"));
        r.setProvinceId(rs.getInt("province_id"));
        r.setRegionName(rs.getString("region_name"));
        r.setAltitudeMinM(getInt(rs, "altitude_min_m"));
        r.setAltitudeMaxM(getInt(rs, "altitude_max_m"));
        r.setSoil(rs.getString("soil"));
        r.setClimate(rs.getString("climate"));
        r.setDescription(rs.getString("description"));
        return r;
    }
}
