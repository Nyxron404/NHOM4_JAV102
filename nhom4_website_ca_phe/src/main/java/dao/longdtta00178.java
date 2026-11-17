package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import uri.DBConnect;

public class longdtta00178 {
    // 1. Lấy tất cả dữ liệu
    public List<Object> getAll() {
        List<Object> list = new ArrayList<>();
        String sql = "SELECT * FROM ten_bang";

        try (Connection conn = DBConnect.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                // Map dữ liệu từ rs vào Model rồi add vào list
                // list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // 2. Tìm theo ID
    public Object getById(int id) {
        String sql = "SELECT * FROM ten_bang WHERE id = ?";

        try (Connection conn = DBConnect.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // 3. Thêm mới
    public boolean insert(Object obj) {
        String sql = "INSERT INTO ten_bang (cot_1, cot_2) VALUES (?, ?)";

        try (Connection conn = DBConnect.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            // ps.setString(1, obj.getCot1());
            // ps.setInt(2, obj.getCot2());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // 4. Cập nhật
    public boolean update(Object obj) {
        String sql = "UPDATE ten_bang SET cot_1 = ?, cot_2 = ? WHERE id = ?";

        try (Connection conn = DBConnect.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            // ps.setString(1, obj.getCot1());
            // ps.setInt(2, obj.getCot2());
            // ps.setInt(3, obj.getId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // 5. Xóa
    public boolean delete(int id) {
        String sql = "DELETE FROM ten_bang WHERE id = ?";

        try (Connection conn = DBConnect.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
