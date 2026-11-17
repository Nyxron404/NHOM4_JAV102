package dao.minhpvth08530;

import models.enums.VariantStatus;
import models.minhpvth08530.FlavorNote;
import models.minhpvth08530.ProductVariant;
import models.minhpvth08530.VariantBrewMethod;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Truy cập bảng product_variants và các bảng N-N của biến thể — UC59..UC71. */
public class ProductVariantDAO {

    private static final String SELECT = """
            SELECT v.*, p.product_name,
                   r.region_name, cv.variety_name, pm.process_name, rl.roast_name,
                   (SELECT COUNT(*) FROM packages pk WHERE pk.variant_id = v.variant_id) AS package_count
            FROM product_variants v
            JOIN products p ON p.product_id = v.product_id
            LEFT JOIN growing_regions r ON r.region_id = v.region_id
            LEFT JOIN coffee_varieties cv ON cv.variety_id = v.variety_id
            LEFT JOIN processing_methods pm ON pm.process_id = v.process_id
            LEFT JOIN roast_levels rl ON rl.roast_level_id = v.roast_level_id
            """;

    private final VariantImageDAO imageDAO = new VariantImageDAO();

    /** UC59 - Danh sách biến thể của một sản phẩm (null = tất cả), lọc theo trạng thái. */
    public List<ProductVariant> findByProduct(Integer productId, VariantStatus status)
            throws SQLException {
        String statusValue = status == null ? null : status.name();
        String sql = SELECT + """
                WHERE (CAST(? AS integer) IS NULL OR v.product_id = ?)
                  AND (CAST(? AS text) IS NULL OR v.status = ?)
                ORDER BY v.product_id, v.display_order, v.variant_id
                """;
        try (Connection connection = DaoSupport.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            DaoSupport.setNullableInt(statement, 1, productId);
            DaoSupport.setNullableInt(statement, 2, productId);
            statement.setString(3, statusValue);
            statement.setString(4, statusValue);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ProductVariant> variants = new ArrayList<>();
                while (resultSet.next()) {
                    variants.add(map(resultSet));
                }
                return variants;
            }
        }
    }

    /** Chi tiết biến thể kèm cách pha gợi ý, nốt hương và ảnh. */
    public ProductVariant findById(int variantId) throws SQLException {
        try (Connection connection = DaoSupport.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     SELECT + " WHERE v.variant_id = ?")) {
            statement.setInt(1, variantId);
            ProductVariant variant;
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                variant = map(resultSet);
            }
            variant.setBrewMethods(findBrewMethods(connection, variantId));
            variant.setFlavorNotes(findFlavorNotes(connection, variantId));
            variant.setImages(imageDAO.findByVariant(connection, variantId));
            return variant;
        }
    }

    /**
     * UC60 - Thêm biến thể. Đủ 4 thuộc tính chuyên môn (vùng trồng, giống, sơ chế, mức rang)
     * và sản phẩm đang kinh doanh thì lưu ACTIVE, ngược lại lưu DRAFT.
     */
    public int insert(ProductVariant variant) throws SQLException {
        String code = DaoSupport.code(variant.getVariantCode(), "Mã biến thể");
        String name = DaoSupport.requiredText(variant.getVariantName(), "Tên biến thể", 200);
        String season = DaoSupport.optionalText(variant.getHarvestSeason(), "Mùa vụ", 20);
        validateAltitude(variant.getAltitudeM());
        int displayOrder = displayOrder(variant.getDisplayOrder());
        try (Connection connection = DaoSupport.openConnection()) {
            boolean productActive = DaoSupport.exists(connection, """
                    SELECT EXISTS (SELECT 1 FROM products WHERE product_id = ? AND status = 'ACTIVE')
                    """, variant.getProductId());
            String status = productActive && variant.isComplete() ? "ACTIVE" : "DRAFT";
            String sql = """
                    INSERT INTO product_variants
                        (product_id, variant_code, variant_name, region_id, variety_id, process_id,
                         roast_level_id, altitude_m, harvest_season, display_order, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, variant.getProductId());
                statement.setString(2, code);
                statement.setString(3, name);
                DaoSupport.setNullableInt(statement, 4, variant.getRegionId());
                DaoSupport.setNullableInt(statement, 5, variant.getVarietyId());
                DaoSupport.setNullableInt(statement, 6, variant.getProcessId());
                DaoSupport.setNullableInt(statement, 7, variant.getRoastLevelId());
                DaoSupport.setNullableInt(statement, 8, variant.getAltitudeM());
                statement.setString(9, season);
                statement.setInt(10, displayOrder);
                statement.setString(11, status);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    return keys.getInt(1);
                }
            }
        }
    }

    /** UC61 - Sửa thông tin mô tả biến thể (mã, tên, độ cao, mùa vụ, thứ tự hiển thị). */
    public boolean update(ProductVariant variant) throws SQLException {
        String code = DaoSupport.code(variant.getVariantCode(), "Mã biến thể");
        String name = DaoSupport.requiredText(variant.getVariantName(), "Tên biến thể", 200);
        String season = DaoSupport.optionalText(variant.getHarvestSeason(), "Mùa vụ", 20);
        validateAltitude(variant.getAltitudeM());
        int displayOrder = displayOrder(variant.getDisplayOrder());
        try (Connection connection = DaoSupport.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE product_variants
                     SET variant_code = ?, variant_name = ?, altitude_m = ?,
                         harvest_season = ?, display_order = ?
                     WHERE variant_id = ?
                     """)) {
            statement.setString(1, code);
            statement.setString(2, name);
            DaoSupport.setNullableInt(statement, 3, variant.getAltitudeM());
            statement.setString(4, season);
            statement.setInt(5, displayOrder);
            statement.setInt(6, variant.getVariantId());
            return statement.executeUpdate() == 1;
        }
    }

    /**
     * UC62 - Xóa biến thể khi chưa có gói bán; ảnh, nốt hương, gợi ý pha xóa cùng giao dịch.
     * @return danh sách public_id ảnh đã xóa (để xóa tiếp trên Cloudinary), null nếu không có biến thể
     */
    public List<String> delete(int variantId) throws SQLException {
        try (Connection connection = DaoSupport.openConnection()) {
            return DaoSupport.inTransaction(connection, c -> {
                if (DaoSupport.exists(c,
                        "SELECT EXISTS (SELECT 1 FROM packages WHERE variant_id = ?)", variantId)) {
                    throw new IllegalStateException(
                            "Biến thể đã có gói bán, không thể xóa. Hãy ngừng kinh doanh biến thể.");
                }
                List<String> publicIds = new ArrayList<>();
                try (PreparedStatement statement = c.prepareStatement(
                        "DELETE FROM variant_images WHERE variant_id = ? RETURNING public_id")) {
                    statement.setInt(1, variantId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            publicIds.add(resultSet.getString(1));
                        }
                    }
                }
                DaoSupport.update(c, "DELETE FROM variant_flavor_notes WHERE variant_id = ?", variantId);
                DaoSupport.update(c, "DELETE FROM variant_brew_methods WHERE variant_id = ?", variantId);
                int deleted = DaoSupport.update(c,
                        "DELETE FROM product_variants WHERE variant_id = ?", variantId);
                return deleted == 1 ? publicIds : null;
            });
        }
    }

    /**
     * UC63 - Hoàn thiện biến thể nháp (thường do nhập kho tự sinh): bổ sung đủ thuộc tính.
     * Biến thể vẫn DRAFT, kích hoạt để bán bằng UC64.
     */
    public boolean completeDraft(ProductVariant variant) throws SQLException {
        String name = DaoSupport.requiredText(variant.getVariantName(), "Tên biến thể", 200);
        String season = DaoSupport.optionalText(variant.getHarvestSeason(), "Mùa vụ", 20);
        validateAltitude(variant.getAltitudeM());
        if (!variant.isComplete()) {
            throw new IllegalArgumentException(
                    "Cần chọn đủ vùng trồng, giống, phương pháp sơ chế và mức rang");
        }
        try (Connection connection = DaoSupport.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE product_variants
                     SET variant_name = ?, region_id = ?, variety_id = ?, process_id = ?,
                         roast_level_id = ?, altitude_m = ?, harvest_season = ?
                     WHERE variant_id = ? AND status = 'DRAFT'
                     """)) {
            statement.setString(1, name);
            statement.setInt(2, variant.getRegionId());
            statement.setInt(3, variant.getVarietyId());
            statement.setInt(4, variant.getProcessId());
            statement.setInt(5, variant.getRoastLevelId());
            DaoSupport.setNullableInt(statement, 6, variant.getAltitudeM());
            statement.setString(7, season);
            statement.setInt(8, variant.getVariantId());
            if (statement.executeUpdate() == 1) {
                return true;
            }
        }
        throw new IllegalStateException("Chỉ hoàn thiện được biến thể đang ở trạng thái nháp");
    }

    /** UC64 - Kích hoạt biến thể: cần đủ 4 thuộc tính và sản phẩm cha đang kinh doanh. */
    public boolean activate(int variantId) throws SQLException {
        ProductVariant variant = findById(variantId);
        if (variant == null) {
            return false;
        }
        if (!variant.isComplete()) {
            throw new IllegalStateException(
                    "Biến thể chưa đủ vùng trồng, giống, phương pháp sơ chế và mức rang");
        }
        try (Connection connection = DaoSupport.openConnection()) {
            int changed = DaoSupport.update(connection, """
                    UPDATE product_variants v SET status = 'ACTIVE'
                    FROM products p
                    WHERE v.variant_id = ? AND p.product_id = v.product_id
                      AND p.status = 'ACTIVE' AND v.status <> 'ACTIVE'
                    """, variantId);
            if (changed == 0 && variant.getStatus() != VariantStatus.ACTIVE) {
                throw new IllegalStateException(
                        "Sản phẩm đã ngừng kinh doanh, hãy kinh doanh lại sản phẩm trước");
            }
            return true;
        }
    }

    /** UC65 - Ngừng kinh doanh biến thể: biến thể và toàn bộ gói bán của nó chuyển INACTIVE. */
    public boolean deactivate(int variantId) throws SQLException {
        try (Connection connection = DaoSupport.openConnection()) {
            return DaoSupport.inTransaction(connection, c -> {
                int changed = DaoSupport.update(c, """
                        UPDATE product_variants SET status = 'INACTIVE'
                        WHERE variant_id = ? AND status <> 'INACTIVE'
                        """, variantId);
                DaoSupport.update(c, """
                        UPDATE packages SET status = 'INACTIVE'
                        WHERE variant_id = ? AND status = 'ACTIVE'
                        """, variantId);
                return changed == 1;
            });
        }
    }

    /** UC66 - Gán vùng trồng cho biến thể. */
    public boolean assignRegion(int variantId, int regionId) throws SQLException {
        return assign("region_id", variantId, regionId);
    }

    /** UC67 - Gán giống cà phê cho biến thể. */
    public boolean assignVariety(int variantId, int varietyId) throws SQLException {
        return assign("variety_id", variantId, varietyId);
    }

    /** UC68 - Gán phương pháp sơ chế cho biến thể. */
    public boolean assignProcessingMethod(int variantId, int processId) throws SQLException {
        return assign("process_id", variantId, processId);
    }

    /** UC69 - Gán mức rang cho biến thể. */
    public boolean assignRoastLevel(int variantId, int roastLevelId) throws SQLException {
        return assign("roast_level_id", variantId, roastLevelId);
    }

    private boolean assign(String column, int variantId, int value) throws SQLException {
        // column chỉ nhận từ 4 hằng ở trên, không lấy từ request nên không có SQL injection
        try (Connection connection = DaoSupport.openConnection()) {
            return DaoSupport.update(connection,
                    "UPDATE product_variants SET " + column + " = ? WHERE variant_id = ?",
                    value, variantId) == 1;
        }
    }

    /**
     * UC70 - Gán phương pháp pha gợi ý. Map truyền vào là trạng thái cuối cùng:
     * khóa = brew_method_id, giá trị = ghi chú (có thể rỗng). Cách pha không còn trong map bị bỏ.
     */
    public void setBrewMethods(int variantId, Map<Integer, String> notesByBrewMethod)
            throws SQLException {
        for (String note : notesByBrewMethod.values()) {
            DaoSupport.optionalText(note, "Ghi chú cách pha", 500);
        }
        try (Connection connection = DaoSupport.openConnection()) {
            DaoSupport.inTransaction(connection, c -> {
                try (PreparedStatement statement = c.prepareStatement("""
                        DELETE FROM variant_brew_methods
                        WHERE variant_id = ? AND NOT (brew_method_id = ANY (?))
                        """)) {
                    statement.setInt(1, variantId);
                    statement.setArray(2, c.createArrayOf("integer",
                            notesByBrewMethod.keySet().toArray(new Integer[0])));
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = c.prepareStatement("""
                        INSERT INTO variant_brew_methods (variant_id, brew_method_id, note)
                        VALUES (?, ?, ?)
                        ON CONFLICT (variant_id, brew_method_id) DO UPDATE SET note = EXCLUDED.note
                        """)) {
                    for (Map.Entry<Integer, String> entry : notesByBrewMethod.entrySet()) {
                        statement.setInt(1, variantId);
                        statement.setInt(2, entry.getKey());
                        statement.setString(3,
                                DaoSupport.optionalText(entry.getValue(), "Ghi chú cách pha", 500));
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                return null;
            });
        }
    }

    /**
     * UC71 - Cập nhật hồ sơ hương vị: 6 điểm cảm quan (1..10, null = chưa chấm)
     * và tập nốt hương (trạng thái cuối cùng).
     */
    public boolean updateFlavorProfile(ProductVariant scores, Collection<Integer> flavorNoteIds)
            throws SQLException {
        Integer[] values = {scores.getAromaScore(), scores.getAcidityScore(),
                scores.getSweetnessScore(), scores.getBitternessScore(), scores.getBodyScore(),
                scores.getAftertasteScore()};
        for (Integer value : values) {
            if (value != null && (value < 1 || value > 10)) {
                throw new IllegalArgumentException("Điểm cảm quan phải từ 1 đến 10");
            }
        }
        Set<Integer> noteIds = new LinkedHashSet<>(flavorNoteIds);
        int variantId = scores.getVariantId();
        try (Connection connection = DaoSupport.openConnection()) {
            return DaoSupport.inTransaction(connection, c -> {
                try (PreparedStatement statement = c.prepareStatement("""
                        UPDATE product_variants
                        SET aroma_score = ?, acidity_score = ?, sweetness_score = ?,
                            bitterness_score = ?, body_score = ?, aftertaste_score = ?
                        WHERE variant_id = ?
                        """)) {
                    for (int index = 0; index < values.length; index++) {
                        DaoSupport.setNullableInt(statement, index + 1, values[index]);
                    }
                    statement.setInt(7, variantId);
                    if (statement.executeUpdate() != 1) {
                        return false;
                    }
                }
                try (PreparedStatement statement = c.prepareStatement("""
                        DELETE FROM variant_flavor_notes
                        WHERE variant_id = ? AND NOT (flavor_note_id = ANY (?))
                        """)) {
                    statement.setInt(1, variantId);
                    statement.setArray(2, c.createArrayOf("integer", noteIds.toArray(new Integer[0])));
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = c.prepareStatement("""
                        INSERT INTO variant_flavor_notes (variant_id, flavor_note_id)
                        VALUES (?, ?) ON CONFLICT DO NOTHING
                        """)) {
                    for (Integer noteId : noteIds) {
                        statement.setInt(1, variantId);
                        statement.setInt(2, noteId);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                return true;
            });
        }
    }

    private List<VariantBrewMethod> findBrewMethods(Connection connection, int variantId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT vbm.variant_id, vbm.brew_method_id, vbm.note, bm.method_name,
                       bm.coffee_water_ratio, bm.water_temp_c, bm.brew_time
                FROM variant_brew_methods vbm
                JOIN brew_methods bm ON bm.brew_method_id = vbm.brew_method_id
                WHERE vbm.variant_id = ?
                ORDER BY bm.method_name
                """)) {
            statement.setInt(1, variantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<VariantBrewMethod> list = new ArrayList<>();
                while (resultSet.next()) {
                    VariantBrewMethod item = new VariantBrewMethod();
                    item.setVariantId(resultSet.getInt("variant_id"));
                    item.setBrewMethodId(resultSet.getInt("brew_method_id"));
                    item.setNote(resultSet.getString("note"));
                    item.setMethodName(resultSet.getString("method_name"));
                    item.setCoffeeWaterRatio(resultSet.getString("coffee_water_ratio"));
                    item.setWaterTempC(resultSet.getString("water_temp_c"));
                    item.setBrewTime(resultSet.getString("brew_time"));
                    list.add(item);
                }
                return list;
            }
        }
    }

    private List<FlavorNote> findFlavorNotes(Connection connection, int variantId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT fn.flavor_note_id, fn.note_name, fn.category
                FROM variant_flavor_notes vfn
                JOIN flavor_notes fn ON fn.flavor_note_id = vfn.flavor_note_id
                WHERE vfn.variant_id = ?
                ORDER BY fn.category, fn.note_name
                """)) {
            statement.setInt(1, variantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<FlavorNote> list = new ArrayList<>();
                while (resultSet.next()) {
                    FlavorNote note = new FlavorNote();
                    note.setFlavorNoteId(resultSet.getInt("flavor_note_id"));
                    note.setNoteName(resultSet.getString("note_name"));
                    note.setCategory(resultSet.getString("category"));
                    list.add(note);
                }
                return list;
            }
        }
    }

    private static void validateAltitude(Integer altitude) {
        if (altitude != null && (altitude < 0 || altitude > 5000)) {
            throw new IllegalArgumentException("Độ cao phải trong khoảng 0 - 5000 m");
        }
    }

    private static int displayOrder(Integer value) {
        int order = value == null ? 0 : value;
        if (order < 0 || order > 32767) {
            throw new IllegalArgumentException("Thứ tự hiển thị phải từ 0 đến 32767");
        }
        return order;
    }

    private static ProductVariant map(ResultSet resultSet) throws SQLException {
        ProductVariant variant = new ProductVariant();
        variant.setVariantId(resultSet.getInt("variant_id"));
        variant.setProductId(resultSet.getInt("product_id"));
        variant.setVariantCode(resultSet.getString("variant_code"));
        variant.setVariantName(resultSet.getString("variant_name"));
        variant.setRegionId(DaoSupport.getNullableInt(resultSet, "region_id"));
        variant.setVarietyId(DaoSupport.getNullableInt(resultSet, "variety_id"));
        variant.setProcessId(DaoSupport.getNullableInt(resultSet, "process_id"));
        variant.setRoastLevelId(DaoSupport.getNullableInt(resultSet, "roast_level_id"));
        variant.setAltitudeM(DaoSupport.getNullableInt(resultSet, "altitude_m"));
        variant.setHarvestSeason(resultSet.getString("harvest_season"));
        variant.setAromaScore(DaoSupport.getNullableInt(resultSet, "aroma_score"));
        variant.setAcidityScore(DaoSupport.getNullableInt(resultSet, "acidity_score"));
        variant.setSweetnessScore(DaoSupport.getNullableInt(resultSet, "sweetness_score"));
        variant.setBitternessScore(DaoSupport.getNullableInt(resultSet, "bitterness_score"));
        variant.setBodyScore(DaoSupport.getNullableInt(resultSet, "body_score"));
        variant.setAftertasteScore(DaoSupport.getNullableInt(resultSet, "aftertaste_score"));
        variant.setDisplayOrder(resultSet.getInt("display_order"));
        variant.setStatus(VariantStatus.valueOf(resultSet.getString("status")));
        variant.setCreatedAt(DaoSupport.getTime(resultSet, "created_at"));
        variant.setProductName(resultSet.getString("product_name"));
        variant.setRegionName(resultSet.getString("region_name"));
        variant.setVarietyName(resultSet.getString("variety_name"));
        variant.setProcessName(resultSet.getString("process_name"));
        variant.setRoastName(resultSet.getString("roast_name"));
        variant.setPackageCount(resultSet.getInt("package_count"));
        return variant;
    }
}
