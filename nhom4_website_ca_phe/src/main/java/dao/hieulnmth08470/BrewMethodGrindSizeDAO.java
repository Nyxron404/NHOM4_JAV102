package dao.hieulnmth08470;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import models.hieulnmth08470.catalog.BrewMethodGrindSize;

/** Gán độ xay phù hợp cho phương pháp pha (bảng brew_method_grind_sizes, N-N) - UC 46. */
public class BrewMethodGrindSizeDAO extends BaseDAO {

    /** Các độ xay đã gán cho 1 phương pháp pha. */
    public List<BrewMethodGrindSize> getByBrewMethod(int brewMethodId) throws SQLException {
        String sql = "SELECT brew_method_id, grind_size_id, is_recommended FROM brew_method_grind_sizes "
                + "WHERE brew_method_id = ?";
        List<BrewMethodGrindSize> list = new ArrayList<>();
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, brewMethodId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BrewMethodGrindSize b = new BrewMethodGrindSize();
                    b.setBrewMethodId(rs.getInt("brew_method_id"));
                    b.setGrindSizeId(rs.getInt("grind_size_id"));
                    b.setIsRecommended(rs.getBoolean("is_recommended"));
                    list.add(b);
                }
            }
        }
        return list;
    }

    /**
     * brew_method_id -> danh sách tên độ xay đã gán (độ xay khuyến nghị có dấu ★),
     * dùng cho cột "Độ xay phù hợp" ở trang danh sách phương pháp pha.
     */
    public Map<Integer, List<String>> grindNamesByBrewMethod() throws SQLException {
        String sql = "SELECT b.brew_method_id, g.grind_name, b.is_recommended "
                + "FROM brew_method_grind_sizes b JOIN grind_sizes g ON g.grind_size_id = b.grind_size_id "
                + "ORDER BY b.brew_method_id, g.sort_order";
        Map<Integer, List<String>> map = new HashMap<>();
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = (rs.getBoolean("is_recommended") ? "★ " : "") + rs.getString("grind_name");
                map.computeIfAbsent(rs.getInt("brew_method_id"), k -> new ArrayList<>()).add(name);
            }
        }
        return map;
    }

    /**
     * Lưu lại toàn bộ phần gán của 1 phương pháp pha: xóa phần cũ rồi thêm phần mới
     * trong cùng 1 giao dịch (lỗi giữa chừng thì giữ nguyên dữ liệu cũ).
     */
    public void replaceAll(int brewMethodId, List<BrewMethodGrindSize> items) throws SQLException {
        inTransaction(con -> {
            try (PreparedStatement del = con.prepareStatement(
                    "DELETE FROM brew_method_grind_sizes WHERE brew_method_id = ?")) {
                del.setInt(1, brewMethodId);
                del.executeUpdate();
            }
            if (!items.isEmpty()) {
                try (PreparedStatement ins = con.prepareStatement(
                        "INSERT INTO brew_method_grind_sizes (brew_method_id, grind_size_id, is_recommended) "
                        + "VALUES (?, ?, ?)")) {
                    for (BrewMethodGrindSize item : items) {
                        ins.setInt(1, brewMethodId);
                        ins.setInt(2, item.getGrindSizeId());
                        ins.setBoolean(3, Boolean.TRUE.equals(item.getIsRecommended()));
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
            }
            return null;
        });
    }
}
