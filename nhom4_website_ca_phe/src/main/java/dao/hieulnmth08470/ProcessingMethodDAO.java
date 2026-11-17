package dao.hieulnmth08470;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import models.hieulnmth08470.catalog.ProcessingMethod;

/** Phương pháp sơ chế (bảng processing_methods) - UC 30-33. */
public class ProcessingMethodDAO extends BaseDAO {

    // 1. Xem danh sách
    public List<ProcessingMethod> search(String keyword) throws SQLException {
        String sql = "SELECT process_id, process_name, description FROM processing_methods"
                + (keyword != null ? " WHERE " + searchCondition("process_name") : "")
                + " ORDER BY process_name";
        List<ProcessingMethod> list = new ArrayList<>();
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
    public ProcessingMethod getById(int id) throws SQLException {
        String sql = "SELECT process_id, process_name, description FROM processing_methods WHERE process_id = ?";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // 3. Thêm mới
    public int insert(ProcessingMethod m) throws SQLException {
        String sql = "INSERT INTO processing_methods (process_name, description) VALUES (?, ?) RETURNING process_id";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, m.getProcessName());
            setString(ps, 2, m.getDescription());
            return returningId(ps);
        }
    }

    // 4. Cập nhật
    public boolean update(ProcessingMethod m) throws SQLException {
        String sql = "UPDATE processing_methods SET process_name = ?, description = ? WHERE process_id = ?";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, m.getProcessName());
            setString(ps, 2, m.getDescription());
            ps.setInt(3, m.getProcessId());
            return ps.executeUpdate() > 0;
        }
    }

    // 5. Xóa
    public boolean delete(int id) throws SQLException {
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement("DELETE FROM processing_methods WHERE process_id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public int countUsage(int id) throws SQLException {
        return count("SELECT count(*) FROM product_variants WHERE process_id = ?", id);
    }

    public Map<Integer, Integer> usageMap() throws SQLException {
        return countMap("SELECT process_id, count(*) FROM product_variants "
                + "WHERE process_id IS NOT NULL GROUP BY process_id");
    }

    private ProcessingMethod mapRow(ResultSet rs) throws SQLException {
        ProcessingMethod m = new ProcessingMethod();
        m.setProcessId(rs.getInt("process_id"));
        m.setProcessName(rs.getString("process_name"));
        m.setDescription(rs.getString("description"));
        return m;
    }
}
