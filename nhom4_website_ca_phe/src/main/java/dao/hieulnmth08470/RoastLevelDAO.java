package dao.hieulnmth08470;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import models.hieulnmth08470.catalog.RoastLevel;

/** Mức rang (bảng roast_levels) - UC 34-37. Danh sách sắp theo sort_order (nhạt -> đậm). */
public class RoastLevelDAO extends BaseDAO {

    private static final String COLUMNS = "roast_level_id, roast_name, sort_order, description";

    // 1. Xem danh sách
    public List<RoastLevel> search(String keyword) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM roast_levels"
                + (keyword != null ? " WHERE " + searchCondition("roast_name") : "")
                + " ORDER BY sort_order";
        List<RoastLevel> list = new ArrayList<>();
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
    public RoastLevel getById(int id) throws SQLException {
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT " + COLUMNS + " FROM roast_levels WHERE roast_level_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** Thứ tự kế tiếp (max + 1) khi người dùng để trống ô "Thứ tự". */
    public int nextSortOrder() throws SQLException {
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM roast_levels");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // 3. Thêm mới
    public int insert(RoastLevel r) throws SQLException {
        String sql = "INSERT INTO roast_levels (roast_name, sort_order, description) VALUES (?, ?, ?) "
                + "RETURNING roast_level_id";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, r.getRoastName());
            ps.setInt(2, r.getSortOrder());
            setString(ps, 3, r.getDescription());
            return returningId(ps);
        }
    }

    // 4. Cập nhật
    public boolean update(RoastLevel r) throws SQLException {
        String sql = "UPDATE roast_levels SET roast_name = ?, sort_order = ?, description = ? WHERE roast_level_id = ?";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, r.getRoastName());
            ps.setInt(2, r.getSortOrder());
            setString(ps, 3, r.getDescription());
            ps.setInt(4, r.getRoastLevelId());
            return ps.executeUpdate() > 0;
        }
    }

    // 5. Xóa
    public boolean delete(int id) throws SQLException {
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement("DELETE FROM roast_levels WHERE roast_level_id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public int countUsage(int id) throws SQLException {
        return count("SELECT count(*) FROM product_variants WHERE roast_level_id = ?", id);
    }

    public Map<Integer, Integer> usageMap() throws SQLException {
        return countMap("SELECT roast_level_id, count(*) FROM product_variants "
                + "WHERE roast_level_id IS NOT NULL GROUP BY roast_level_id");
    }

    private RoastLevel mapRow(ResultSet rs) throws SQLException {
        RoastLevel r = new RoastLevel();
        r.setRoastLevelId(rs.getInt("roast_level_id"));
        r.setRoastName(rs.getString("roast_name"));
        r.setSortOrder(rs.getInt("sort_order"));
        r.setDescription(rs.getString("description"));
        return r;
    }
}
