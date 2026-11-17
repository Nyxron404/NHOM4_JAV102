package dao.hieulnmth08470;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import models.hieulnmth08470.catalog.CoffeeVariety;

/** Giống cà phê (bảng coffee_varieties) - UC 26-29. */
public class CoffeeVarietyDAO extends BaseDAO {

    // 1. Xem danh sách (tìm theo tên không dấu, lọc theo loài)
    public List<CoffeeVariety> search(String keyword, Integer speciesId) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT v.variety_id, v.species_id, v.variety_name, v.description "
                + "FROM coffee_varieties v JOIN coffee_species s ON s.species_id = v.species_id WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (keyword != null) {
            sql.append(" AND ").append(searchCondition("v.variety_name"));
            params.add(escapeLike(keyword));
        }
        if (speciesId != null) {
            sql.append(" AND v.species_id = ?");
            params.add(speciesId);
        }
        sql.append(" ORDER BY s.species_name, v.variety_name");

        List<CoffeeVariety> list = new ArrayList<>();
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
    public CoffeeVariety getById(int id) throws SQLException {
        String sql = "SELECT variety_id, species_id, variety_name, description FROM coffee_varieties WHERE variety_id = ?";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // 3. Thêm mới
    public int insert(CoffeeVariety v) throws SQLException {
        String sql = "INSERT INTO coffee_varieties (species_id, variety_name, description) VALUES (?, ?, ?) "
                + "RETURNING variety_id";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, v.getSpeciesId());
            ps.setString(2, v.getVarietyName());
            setString(ps, 3, v.getDescription());
            return returningId(ps);
        }
    }

    // 4. Cập nhật
    public boolean update(CoffeeVariety v) throws SQLException {
        String sql = "UPDATE coffee_varieties SET species_id = ?, variety_name = ?, description = ? WHERE variety_id = ?";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, v.getSpeciesId());
            ps.setString(2, v.getVarietyName());
            setString(ps, 3, v.getDescription());
            ps.setInt(4, v.getVarietyId());
            return ps.executeUpdate() > 0;
        }
    }

    // 5. Xóa
    public boolean delete(int id) throws SQLException {
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement("DELETE FROM coffee_varieties WHERE variety_id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** Số biến thể sản phẩm đang dùng giống này. */
    public int countUsage(int id) throws SQLException {
        return count("SELECT count(*) FROM product_variants WHERE variety_id = ?", id);
    }

    public Map<Integer, Integer> usageMap() throws SQLException {
        return countMap("SELECT variety_id, count(*) FROM product_variants "
                + "WHERE variety_id IS NOT NULL GROUP BY variety_id");
    }

    private CoffeeVariety mapRow(ResultSet rs) throws SQLException {
        CoffeeVariety v = new CoffeeVariety();
        v.setVarietyId(rs.getInt("variety_id"));
        v.setSpeciesId(rs.getInt("species_id"));
        v.setVarietyName(rs.getString("variety_name"));
        v.setDescription(rs.getString("description"));
        return v;
    }
}
