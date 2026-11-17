package dao.hieulnmth08470;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import models.hieulnmth08470.catalog.GrindSize;

/** Độ xay / dạng sản phẩm (bảng grind_sizes) - UC 42-45. Sắp theo sort_order (thô -> mịn). */
public class GrindSizeDAO extends BaseDAO {

    private static final String COLUMNS = "grind_size_id, grind_code, grind_name, is_whole_bean, sort_order, description";

    // 1. Xem danh sách (tìm theo tên không dấu hoặc theo mã)
    public List<GrindSize> search(String keyword) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM grind_sizes"
                + (keyword != null ? " WHERE " + searchCondition("grind_name") + " OR grind_code ILIKE '%' || ? || '%'" : "")
                + " ORDER BY sort_order";
        List<GrindSize> list = new ArrayList<>();
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            if (keyword != null) {
                ps.setString(1, escapeLike(keyword));
                ps.setString(2, escapeLike(keyword));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public List<GrindSize> getAll() throws SQLException {
        return search(null);
    }

    // 2. Tìm theo ID
    public GrindSize getById(int id) throws SQLException {
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT " + COLUMNS + " FROM grind_sizes WHERE grind_size_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public int nextSortOrder() throws SQLException {
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM grind_sizes");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // 3. Thêm mới
    public int insert(GrindSize g) throws SQLException {
        String sql = "INSERT INTO grind_sizes (grind_code, grind_name, is_whole_bean, sort_order, description) "
                + "VALUES (?, ?, ?, ?, ?) RETURNING grind_size_id";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            setValues(ps, g);
            return returningId(ps);
        }
    }

    // 4. Cập nhật
    public boolean update(GrindSize g) throws SQLException {
        String sql = "UPDATE grind_sizes SET grind_code = ?, grind_name = ?, is_whole_bean = ?, sort_order = ?, "
                + "description = ? WHERE grind_size_id = ?";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            setValues(ps, g);
            ps.setInt(6, g.getGrindSizeId());
            return ps.executeUpdate() > 0;
        }
    }

    // 5. Xóa: bỏ luôn các dòng gán cho phương pháp pha (cùng 1 giao dịch)
    public boolean delete(int id) throws SQLException {
        return inTransaction(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM brew_method_grind_sizes WHERE grind_size_id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM grind_sizes WHERE grind_size_id = ?")) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            }
        });
    }

    /** Số gói bán (SKU) đang dùng độ xay này. */
    public int countUsage(int id) throws SQLException {
        return count("SELECT count(*) FROM packages WHERE grind_size_id = ?", id);
    }

    public Map<Integer, Integer> usageMap() throws SQLException {
        return countMap("SELECT grind_size_id, count(*) FROM packages GROUP BY grind_size_id");
    }

    private void setValues(PreparedStatement ps, GrindSize g) throws SQLException {
        ps.setString(1, g.getGrindCode());
        ps.setString(2, g.getGrindName());
        ps.setBoolean(3, Boolean.TRUE.equals(g.getIsWholeBean()));
        ps.setInt(4, g.getSortOrder());
        setString(ps, 5, g.getDescription());
    }

    private GrindSize mapRow(ResultSet rs) throws SQLException {
        GrindSize g = new GrindSize();
        g.setGrindSizeId(rs.getInt("grind_size_id"));
        g.setGrindCode(rs.getString("grind_code"));
        g.setGrindName(rs.getString("grind_name"));
        g.setIsWholeBean(rs.getBoolean("is_whole_bean"));
        g.setSortOrder(rs.getInt("sort_order"));
        g.setDescription(rs.getString("description"));
        return g;
    }
}
