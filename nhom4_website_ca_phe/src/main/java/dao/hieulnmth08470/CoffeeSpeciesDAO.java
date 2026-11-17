package dao.hieulnmth08470;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import models.hieulnmth08470.catalog.CoffeeSpecies;

/** Loài cà phê (bảng coffee_species) - UC 22-25. */
public class CoffeeSpeciesDAO extends BaseDAO {

    // 1. Xem danh sách (keyword = null thì lấy tất cả)
    public List<CoffeeSpecies> search(String keyword) throws SQLException {
        String sql = "SELECT species_id, species_name, description FROM coffee_species"
                + (keyword != null ? " WHERE " + searchCondition("species_name") : "")
                + " ORDER BY species_name";
        List<CoffeeSpecies> list = new ArrayList<>();
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

    public List<CoffeeSpecies> getAll() throws SQLException {
        return search(null);
    }

    // 2. Tìm theo ID
    public CoffeeSpecies getById(int id) throws SQLException {
        String sql = "SELECT species_id, species_name, description FROM coffee_species WHERE species_id = ?";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // 3. Thêm mới
    public int insert(CoffeeSpecies s) throws SQLException {
        String sql = "INSERT INTO coffee_species (species_name, description) VALUES (?, ?) RETURNING species_id";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, s.getSpeciesName());
            setString(ps, 2, s.getDescription());
            return returningId(ps);
        }
    }

    // 4. Cập nhật
    public boolean update(CoffeeSpecies s) throws SQLException {
        String sql = "UPDATE coffee_species SET species_name = ?, description = ? WHERE species_id = ?";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, s.getSpeciesName());
            setString(ps, 2, s.getDescription());
            ps.setInt(3, s.getSpeciesId());
            return ps.executeUpdate() > 0;
        }
    }

    // 5. Xóa
    public boolean delete(int id) throws SQLException {
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement("DELETE FROM coffee_species WHERE species_id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** Số giống cà phê thuộc loài này (> 0 thì không được xóa). */
    public int countUsage(int id) throws SQLException {
        return count("SELECT count(*) FROM coffee_varieties WHERE species_id = ?", id);
    }

    /** species_id -> số giống. */
    public Map<Integer, Integer> usageMap() throws SQLException {
        return countMap("SELECT species_id, count(*) FROM coffee_varieties GROUP BY species_id");
    }

    private CoffeeSpecies mapRow(ResultSet rs) throws SQLException {
        CoffeeSpecies s = new CoffeeSpecies();
        s.setSpeciesId(rs.getInt("species_id"));
        s.setSpeciesName(rs.getString("species_name"));
        s.setDescription(rs.getString("description"));
        return s;
    }
}
