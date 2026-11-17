package dao.minhpvth08530;

import models.minhpvth08530.LookupItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Đọc các danh mục (do phần Danh mục quản lý) để làm dropdown trên trang sản phẩm.
 * Chỉ đọc, không sửa.
 */
public class LookupDAO {

    private static final Map<String, String> QUERIES = new LinkedHashMap<>();

    static {
        QUERIES.put("regions", """
                SELECT r.region_id AS id, r.region_name AS name, pr.province_name AS detail
                FROM growing_regions r JOIN provinces pr ON pr.province_id = r.province_id
                ORDER BY r.region_name""");
        QUERIES.put("varieties", """
                SELECT v.variety_id AS id, v.variety_name AS name, s.species_name AS detail
                FROM coffee_varieties v JOIN coffee_species s ON s.species_id = v.species_id
                ORDER BY s.species_name, v.variety_name""");
        QUERIES.put("processes", """
                SELECT process_id AS id, process_name AS name, NULL AS detail
                FROM processing_methods ORDER BY process_name""");
        QUERIES.put("roastLevels", """
                SELECT roast_level_id AS id, roast_name AS name, NULL AS detail
                FROM roast_levels ORDER BY sort_order""");
        QUERIES.put("brewMethods", """
                SELECT brew_method_id AS id, method_name AS name,
                       concat_ws(' · ', coffee_water_ratio, water_temp_c, brew_time) AS detail
                FROM brew_methods ORDER BY method_name""");
        QUERIES.put("grindSizes", """
                SELECT grind_size_id AS id, grind_name AS name,
                       CASE WHEN is_whole_bean THEN 'Nguyên hạt' END AS detail
                FROM grind_sizes ORDER BY sort_order""");
        QUERIES.put("flavorNotes", """
                SELECT flavor_note_id AS id, note_name AS name, category AS detail
                FROM flavor_notes ORDER BY category, note_name""");
    }

    /** Toàn bộ danh mục cần cho trang, mỗi khóa là một danh sách. */
    public Map<String, List<LookupItem>> findAll() throws SQLException {
        Map<String, List<LookupItem>> result = new LinkedHashMap<>();
        try (Connection connection = DaoSupport.openConnection()) {
            for (Map.Entry<String, String> entry : QUERIES.entrySet()) {
                result.put(entry.getKey(), query(connection, entry.getValue()));
            }
        }
        return result;
    }

    /**
     * Người dùng mặc định khi chưa có phiên đăng nhập (module tài khoản chưa xong):
     * tài khoản ACTIVE có user_id nhỏ nhất. Trả null nếu chưa có tài khoản nào.
     */
    public Integer findDefaultUserId() throws SQLException {
        try (Connection connection = DaoSupport.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT user_id FROM users WHERE status = 'ACTIVE' ORDER BY user_id LIMIT 1");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt(1) : null;
        }
    }

    private static List<LookupItem> query(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<LookupItem> items = new ArrayList<>();
            while (resultSet.next()) {
                items.add(new LookupItem(resultSet.getInt("id"), resultSet.getString("name"),
                        resultSet.getString("detail")));
            }
            return items;
        }
    }
}
