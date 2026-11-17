package dao.hieulnmth08470;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import models.hieulnmth08470.catalog.BrewMethod;

/** Phương pháp pha (bảng brew_methods) - UC 38-41. */
public class BrewMethodDAO extends BaseDAO {

    private static final String COLUMNS =
            "brew_method_id, method_name, coffee_water_ratio, water_temp_c, brew_time, guide";

    // 1. Xem danh sách
    public List<BrewMethod> search(String keyword) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM brew_methods"
                + (keyword != null ? " WHERE " + searchCondition("method_name") : "")
                + " ORDER BY method_name";
        List<BrewMethod> list = new ArrayList<>();
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            if (keyword != null) ps.setString(1, escapeLike(keyword));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    // 2. Tìm theo ID
    public BrewMethod getById(int id) throws SQLException {
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT " + COLUMNS + " FROM brew_methods WHERE brew_method_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // 3. Thêm mới
    public int insert(BrewMethod m) throws SQLException {
        String sql = "INSERT INTO brew_methods (method_name, coffee_water_ratio, water_temp_c, brew_time, guide) "
                + "VALUES (?, ?, ?, ?, ?) RETURNING brew_method_id";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            setValues(ps, m);
            return returningId(ps);
        }
    }

    // 4. Cập nhật
    public boolean update(BrewMethod m) throws SQLException {
        String sql = "UPDATE brew_methods SET method_name = ?, coffee_water_ratio = ?, water_temp_c = ?, "
                + "brew_time = ?, guide = ? WHERE brew_method_id = ?";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            setValues(ps, m);
            ps.setInt(6, m.getBrewMethodId());
            return ps.executeUpdate() > 0;
        }
    }

    // 5. Xóa: bỏ luôn các dòng gán độ xay của phương pháp này (cùng 1 giao dịch)
    public boolean delete(int id) throws SQLException {
        return inTransaction(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM brew_method_grind_sizes WHERE brew_method_id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM brew_methods WHERE brew_method_id = ?")) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            }
        });
    }

    /** Số biến thể sản phẩm đang gợi ý phương pháp pha này (bảng variant_brew_methods). */
    public int countUsage(int id) throws SQLException {
        return count("SELECT count(*) FROM variant_brew_methods WHERE brew_method_id = ?", id);
    }

    public Map<Integer, Integer> usageMap() throws SQLException {
        return countMap("SELECT brew_method_id, count(*) FROM variant_brew_methods GROUP BY brew_method_id");
    }

    private void setValues(PreparedStatement ps, BrewMethod m) throws SQLException {
        ps.setString(1, m.getMethodName());
        setString(ps, 2, m.getCoffeeWaterRatio());
        setString(ps, 3, m.getWaterTempC());
        setString(ps, 4, m.getBrewTime());
        setString(ps, 5, m.getGuide());
    }

    private BrewMethod mapRow(ResultSet rs) throws SQLException {
        BrewMethod m = new BrewMethod();
        m.setBrewMethodId(rs.getInt("brew_method_id"));
        m.setMethodName(rs.getString("method_name"));
        m.setCoffeeWaterRatio(rs.getString("coffee_water_ratio"));
        m.setWaterTempC(rs.getString("water_temp_c"));
        m.setBrewTime(rs.getString("brew_time"));
        m.setGuide(rs.getString("guide"));
        return m;
    }
}
