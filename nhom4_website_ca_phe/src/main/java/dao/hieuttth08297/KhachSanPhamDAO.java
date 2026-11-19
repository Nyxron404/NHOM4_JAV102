package dao.hieuttth08297;

import models.hieuttth08297.TrangSanPham.*;
import uri.DBConnect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KhachSanPhamDAO {

    private static Connection open() throws SQLException {
        Connection connection = DBConnect.getConnection();
        if (connection == null) {
            throw new SQLException("Không kết nối được cơ sở dữ liệu", "08001");
        }
        return connection;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public Trang findByToken(String token) throws SQLException {
        try (Connection con = open()) {
            int productId;
            SanPham product;
            try (PreparedStatement ps = con.prepareStatement("""
                    SELECT product_id, product_code, product_name, description, status
                    FROM products WHERE qr_token = ?""")) {
                ps.setString(1, token);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    productId = rs.getInt("product_id");
                    product = new SanPham(rs.getString("product_code"), rs.getString("product_name"),
                            rs.getString("description"), "ACTIVE".equals(rs.getString("status")));
                }
            }
            if (!product.active()) {
                return new Trang(product, null, List.of());
            }
            List<BienThe> variants = loadVariants(con, productId);
            Integer defaultId = variants.isEmpty() ? null : variants.get(0).variantId();
            return new Trang(product, defaultId, variants);
        }
    }

    private List<BienThe> loadVariants(Connection con, int productId) throws SQLException {
        Map<Integer, List<NotHuong>> notes = loadNotes(con, productId);
        Map<Integer, List<CachPha>> brews = loadBrews(con, productId);
        Map<Integer, List<String>> images = loadImages(con, productId);

        String sql = """
                SELECT v.variant_id, v.variant_code, v.variant_name, v.altitude_m, v.harvest_season,
                       v.aroma_score, v.acidity_score, v.sweetness_score,
                       v.bitterness_score, v.body_score, v.aftertaste_score,
                       r.region_name, pr.province_name, r.altitude_min_m, r.altitude_max_m,
                       r.soil, r.climate, r.description AS region_desc,
                       cv.variety_name, sp.species_name, cv.description AS variety_desc,
                       pm.process_name, pm.description AS process_desc,
                       rl.roast_name, rl.description AS roast_desc
                FROM product_variants v
                LEFT JOIN growing_regions r ON r.region_id = v.region_id
                LEFT JOIN provinces pr ON pr.province_id = r.province_id
                LEFT JOIN coffee_varieties cv ON cv.variety_id = v.variety_id
                LEFT JOIN coffee_species sp ON sp.species_id = cv.species_id
                LEFT JOIN processing_methods pm ON pm.process_id = v.process_id
                LEFT JOIN roast_levels rl ON rl.roast_level_id = v.roast_level_id
                WHERE v.product_id = ? AND v.status = 'ACTIVE'
                ORDER BY v.display_order, v.variant_id""";
        List<BienThe> result = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("variant_id");
                    VungTrong region = rs.getString("region_name") == null ? null
                            : new VungTrong(rs.getString("region_name"), rs.getString("province_name"),
                                    nullableInt(rs, "altitude_min_m"), nullableInt(rs, "altitude_max_m"),
                                    rs.getString("soil"), rs.getString("climate"),
                                    rs.getString("region_desc"));
                    Giong variety = rs.getString("variety_name") == null ? null
                            : new Giong(rs.getString("variety_name"), rs.getString("species_name"),
                                    rs.getString("variety_desc"));
                    TenMoTa process = rs.getString("process_name") == null ? null
                            : new TenMoTa(rs.getString("process_name"), rs.getString("process_desc"));
                    TenMoTa roast = rs.getString("roast_name") == null ? null
                            : new TenMoTa(rs.getString("roast_name"), rs.getString("roast_desc"));
                    DiemHuongVi scores = new DiemHuongVi(nullableInt(rs, "aroma_score"),
                            nullableInt(rs, "acidity_score"), nullableInt(rs, "sweetness_score"),
                            nullableInt(rs, "bitterness_score"), nullableInt(rs, "body_score"),
                            nullableInt(rs, "aftertaste_score"));
                    result.add(new BienThe(id, rs.getString("variant_code"), rs.getString("variant_name"),
                            nullableInt(rs, "altitude_m"), rs.getString("harvest_season"),
                            region, variety, process, roast, scores,
                            notes.getOrDefault(id, List.of()), brews.getOrDefault(id, List.of()),
                            images.getOrDefault(id, List.of())));
                }
            }
        }
        return result;
    }

    private Map<Integer, List<NotHuong>> loadNotes(Connection con, int productId) throws SQLException {
        Map<Integer, List<NotHuong>> map = new HashMap<>();
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT vfn.variant_id, fn.note_name, fn.category
                FROM variant_flavor_notes vfn
                JOIN flavor_notes fn ON fn.flavor_note_id = vfn.flavor_note_id
                JOIN product_variants v ON v.variant_id = vfn.variant_id
                WHERE v.product_id = ? AND v.status = 'ACTIVE'
                ORDER BY fn.category, fn.note_name""")) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.computeIfAbsent(rs.getInt("variant_id"), k -> new ArrayList<>())
                            .add(new NotHuong(rs.getString("note_name"), rs.getString("category")));
                }
            }
        }
        return map;
    }

    private Map<Integer, List<CachPha>> loadBrews(Connection con, int productId) throws SQLException {
        Map<Integer, List<CachPha>> map = new HashMap<>();
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT vbm.variant_id, vbm.note, bm.method_name, bm.coffee_water_ratio,
                       bm.water_temp_c, bm.brew_time, bm.guide
                FROM variant_brew_methods vbm
                JOIN brew_methods bm ON bm.brew_method_id = vbm.brew_method_id
                JOIN product_variants v ON v.variant_id = vbm.variant_id
                WHERE v.product_id = ? AND v.status = 'ACTIVE'
                ORDER BY bm.method_name""")) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.computeIfAbsent(rs.getInt("variant_id"), k -> new ArrayList<>())
                            .add(new CachPha(rs.getString("method_name"), rs.getString("coffee_water_ratio"),
                                    rs.getString("water_temp_c"), rs.getString("brew_time"),
                                    rs.getString("guide"), rs.getString("note")));
                }
            }
        }
        return map;
    }

    private Map<Integer, List<String>> loadImages(Connection con, int productId) throws SQLException {
        Map<Integer, List<String>> map = new HashMap<>();
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT vi.variant_id, vi.image_url
                FROM variant_images vi
                JOIN product_variants v ON v.variant_id = vi.variant_id
                WHERE v.product_id = ? AND v.status = 'ACTIVE'
                ORDER BY vi.sort_order, vi.image_id""")) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.computeIfAbsent(rs.getInt("variant_id"), k -> new ArrayList<>())
                            .add(rs.getString("image_url"));
                }
            }
        }
        return map;
    }
}