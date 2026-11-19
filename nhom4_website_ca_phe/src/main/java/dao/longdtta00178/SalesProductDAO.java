package dao.longdtta00178;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import models.longdtta00178.SalesProductCard;
import models.longdtta00178.SellablePackage;
import uri.DBConnect;

/**
 * Dữ liệu cho cột trái màn bán hàng: thẻ cà phê + gói bán (UC 131 tìm kiếm, 136 tồn còn hạn).
 */
public class SalesProductDAO {

    /** 1 dòng = 1 gói ACTIVE kèm thông tin biến thể; sắp xếp sẵn theo sản phẩm, biến thể, độ xay, trọng lượng. */
    private static final String SQL_SELLABLE = """
            WITH params AS (
                SELECT CAST(? AS TEXT)    AS keyword,
                       CAST(? AS TEXT)    AS species_name,
                       CAST(? AS BOOLEAN) AS whole_bean,
                       CAST(? AS INT)     AS region_id,
                       CAST(? AS INT)     AS roast_level_id,
                       CAST(? AS BOOLEAN) AS in_stock_only,
                       (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS today,
                       COALESCE((SELECT setting_value::int FROM system_settings
                                 WHERE setting_key = 'inventory.near_expiry_days'), 30) AS near_days
            ), lot_stock AS (            -- tồn từng lô còn hạn, còn hàng
                SELECT pil.package_id, lo.lot_id, lo.expiry_date, SUM(m.quantity_change) AS qty
                FROM lots lo
                JOIN purchase_invoice_lines pil ON pil.purchase_line_id = lo.purchase_line_id
                JOIN stock_movements m          ON m.lot_id = lo.lot_id
                CROSS JOIN params
                WHERE lo.expiry_date >= params.today
                GROUP BY pil.package_id, lo.lot_id, lo.expiry_date
                HAVING SUM(m.quantity_change) > 0
            ), package_stock AS (        -- tồn theo gói
                SELECT package_id, SUM(qty) AS sellable_qty, MIN(expiry_date) AS nearest_expiry
                FROM lot_stock
                GROUP BY package_id
            )
            SELECT v.variant_id, v.variant_name, pr.product_name,
                   sp.species_name, r.region_name, pm.process_name, rl.roast_name,
                   (SELECT i.image_url FROM variant_images i
                    WHERE i.variant_id = v.variant_id ORDER BY i.sort_order LIMIT 1)          AS image_url,
                   (SELECT string_agg(f.note_name, ', ' ORDER BY f.note_name)
                    FROM variant_flavor_notes vf JOIN flavor_notes f ON f.flavor_note_id = vf.flavor_note_id
                    WHERE vf.variant_id = v.variant_id)                                       AS flavor_notes,
                   pk.package_id, pk.sku, pk.weight_g, g.grind_name, g.is_whole_bean, pk.min_stock_level,
                   cp.sale_price,
                   COALESCE(ps.sellable_qty, 0)                                               AS sellable_qty,
                   COALESCE(ps.nearest_expiry <= params.today + params.near_days, false)      AS has_near_expiry
            FROM packages pk
            JOIN product_variants v        ON v.variant_id = pk.variant_id
            JOIN products pr               ON pr.product_id = v.product_id
            JOIN grind_sizes g             ON g.grind_size_id = pk.grind_size_id
            LEFT JOIN growing_regions r    ON r.region_id = v.region_id
            LEFT JOIN coffee_varieties cv  ON cv.variety_id = v.variety_id
            LEFT JOIN coffee_species sp    ON sp.species_id = cv.species_id
            LEFT JOIN processing_methods pm ON pm.process_id = v.process_id
            LEFT JOIN roast_levels rl      ON rl.roast_level_id = v.roast_level_id
            LEFT JOIN v_package_current_price cp ON cp.package_id = pk.package_id
            LEFT JOIN package_stock ps     ON ps.package_id = pk.package_id
            CROSS JOIN params
            WHERE pk.status = 'ACTIVE' AND v.status = 'ACTIVE' AND pr.status = 'ACTIVE'
              AND (params.keyword IS NULL
                   OR pk.sku ILIKE '%' || params.keyword || '%'
                   OR f_search_key(pr.product_name) LIKE '%' || f_search_key(params.keyword) || '%'
                                      OR f_search_key(v.variant_name)  LIKE '%' || f_search_key(params.keyword) || '%'
                   OR EXISTS (SELECT 1 FROM lots lo2                                   -- tìm theo mã lô
                              JOIN purchase_invoice_lines pil2 ON pil2.purchase_line_id = lo2.purchase_line_id
                              WHERE pil2.package_id = pk.package_id
                                AND lo2.lot_code ILIKE '%' || params.keyword || '%'))
              AND (params.species_name   IS NULL OR sp.species_name    = params.species_name)
              AND (params.whole_bean     IS NULL OR g.is_whole_bean     = params.whole_bean)
              AND (params.region_id      IS NULL OR v.region_id         = params.region_id)
              AND (params.roast_level_id IS NULL OR v.roast_level_id    = params.roast_level_id)
              AND (params.in_stock_only  IS NOT TRUE OR COALESCE(ps.sellable_qty, 0) > 0)
            ORDER BY pr.product_name, v.display_order, v.variant_id, g.sort_order, pk.weight_g
            """;

    /**
     * Lấy thẻ cà phê đang bán. Tham số null = không lọc.
     *
     * @param keyword      tên sản phẩm / biến thể (không dấu) hoặc SKU
     * @param speciesName  Arabica, Robusta, Liberica...
     * @param wholeBean    true = nguyên hạt, false = xay sẵn
     * @param regionId     vùng trồng
     * @param roastLevelId mức rang
     * @param inStockOnly  true = bỏ các gói hết hàng
     */
    public List<SalesProductCard> findSellableCards(String keyword, String speciesName, Boolean wholeBean,
            Integer regionId, Integer roastLevelId, boolean inStockOnly) throws SQLException {

        // Gom các dòng cùng variant_id vào 1 thẻ; LinkedHashMap giữ đúng thứ tự ORDER BY
        Map<Integer, SalesProductCard> cards = new LinkedHashMap<>();

        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(SQL_SELLABLE)) {
            setText(ps, 1, keyword);
            setText(ps, 2, speciesName);
            if (wholeBean == null) ps.setNull(3, Types.BOOLEAN); else ps.setBoolean(3, wholeBean);
            setInt(ps, 4, regionId);
            setInt(ps, 5, roastLevelId);
            ps.setBoolean(6, inStockOnly);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int variantId = rs.getInt("variant_id");
                    SalesProductCard card = cards.get(variantId);
                    if (card == null) {
                        card = new SalesProductCard();
                        card.setVariantId(variantId);
                        card.setVariantName(rs.getString("variant_name"));
                        card.setProductName(rs.getString("product_name"));
                        card.setSpeciesName(rs.getString("species_name"));
                        card.setRegionName(rs.getString("region_name"));
                        card.setProcessName(rs.getString("process_name"));
                        card.setRoastName(rs.getString("roast_name"));
                        card.setImageUrl(rs.getString("image_url"));
                        card.setFlavorNotes(rs.getString("flavor_notes"));
                        cards.put(variantId, card);
                    }

                    SellablePackage p = new SellablePackage();
                    p.setPackageId(rs.getInt("package_id"));
                    p.setSku(rs.getString("sku"));
                    p.setWeightG(rs.getInt("weight_g"));
                    p.setGrindName(rs.getString("grind_name"));
                    p.setWholeBean(rs.getBoolean("is_whole_bean"));
                    p.setMinStockLevel(rs.getInt("min_stock_level"));
                    p.setSalePrice(rs.getBigDecimal("sale_price"));      // null nếu chưa có giá
                    p.setSellableQty(rs.getInt("sellable_qty"));
                    p.setNearExpiry(rs.getBoolean("has_near_expiry"));
                    card.getPackages().add(p);
                }
            }
        }
                attachLots(new ArrayList<>(cards.values()), keyword);
        return new ArrayList<>(cards.values());
    }

    /** Gắn danh sách lô bán được vào từng gói (ô chọn lô) + đánh dấu lô khớp mã lô đang tìm */
    private void attachLots(List<SalesProductCard> cards, String keyword) throws SQLException {
        Map<Integer, SellablePackage> byId = new LinkedHashMap<>();
        for (SalesProductCard c : cards) for (SellablePackage p : c.getPackages()) byId.put(p.getPackageId(), p);
        if (byId.isEmpty()) return;
        String sql = """
                SELECT pil.package_id, lo.lot_id, lo.lot_code, lo.expiry_date, SUM(m.quantity_change) AS qty,
                       lo.expiry_date <= (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
                           + COALESCE((SELECT setting_value::int FROM system_settings
                                       WHERE setting_key = 'inventory.near_expiry_days'), 30) AS near_expiry
                FROM lots lo
                JOIN purchase_invoice_lines pil ON pil.purchase_line_id = lo.purchase_line_id
                JOIN stock_movements m          ON m.lot_id = lo.lot_id
                WHERE pil.package_id = ANY (?)
                  AND lo.expiry_date >= (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
                GROUP BY pil.package_id, lo.lot_id, lo.lot_code, lo.expiry_date
                HAVING SUM(m.quantity_change) > 0
                ORDER BY lo.expiry_date, lo.lot_id
                """;
        String kw = keyword == null ? null : keyword.trim().toUpperCase();
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setArray(1, con.createArrayOf("integer", byId.keySet().toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SellablePackage p = byId.get(rs.getInt("package_id"));
                    Map<String, Object> lot = new LinkedHashMap<>();
                    lot.put("lotId", rs.getLong("lot_id"));
                    lot.put("lotCode", rs.getString("lot_code"));
                    lot.put("expiryDate", rs.getObject("expiry_date", java.time.LocalDate.class));
                    lot.put("qty", rs.getInt("qty"));
                    lot.put("nearExpiry", rs.getBoolean("near_expiry"));
                    p.getLots().add(lot);
                                        if (kw != null && !kw.isEmpty() && p.getMatchedLotId() == null
                            && rs.getString("lot_code").toUpperCase().contains(kw)) {
                        p.setMatchedLotId(rs.getLong("lot_id"));
                    }
                }
            }

            // Tìm theo mã lô mà lô đó không bán được -> ghi chú, không chọn sẵn lô khác
            if (kw != null && !kw.isEmpty()) {
                String sqlMatched = """
                        SELECT pil.package_id, lo.lot_code, lo.expiry_date,
                               COALESCE(SUM(m.quantity_change), 0) AS qty,
                               lo.expiry_date < (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS expired
                        FROM lots lo
                        JOIN purchase_invoice_lines pil ON pil.purchase_line_id = lo.purchase_line_id
                        LEFT JOIN stock_movements m     ON m.lot_id = lo.lot_id
                        WHERE pil.package_id = ANY (?) AND lo.lot_code ILIKE '%' || ? || '%'
                        GROUP BY pil.package_id, lo.lot_id, lo.lot_code, lo.expiry_date
                        ORDER BY lo.expiry_date DESC
                        """;
                try (PreparedStatement ps2 = con.prepareStatement(sqlMatched)) {
                    ps2.setArray(1, con.createArrayOf("integer", byId.keySet().toArray()));
                    ps2.setString(2, keyword.trim());
                    try (ResultSet rs = ps2.executeQuery()) {
                        while (rs.next()) {
                            SellablePackage p = byId.get(rs.getInt("package_id"));
                            if (p.getMatchedLotId() != null || p.getMatchedLotNote() != null) continue;
                            java.time.LocalDate exp = rs.getObject("expiry_date", java.time.LocalDate.class);
                            p.setMatchedLotNote("Lô " + rs.getString("lot_code") + (rs.getBoolean("expired")
                                    ? " đã hết hạn ngày " + exp.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                      + ", không bán được"
                                    : " đã hết hàng trên hệ thống (tồn " + rs.getInt("qty")
                                      + "). Có hàng thật thì quét QR trên túi"));
                        }
                    }
                }
            }
        }
    }

    /** Danh mục cho các ô lọc (loài, vùng trồng, mức rang): tải 1 lần khi mở trang. */
    public Map<String, List<Map<String, Object>>> findFilterOptions() throws SQLException {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        try (Connection con = connect()) {
            result.put("species", lookup(con,
                    "SELECT species_id AS id, species_name AS name FROM coffee_species ORDER BY species_name"));
            result.put("regions", lookup(con,
                    "SELECT region_id AS id, region_name AS name FROM growing_regions ORDER BY region_name"));
            result.put("roastLevels", lookup(con,
                    "SELECT roast_level_id AS id, roast_name AS name FROM roast_levels ORDER BY sort_order"));
        }
        return result;
    }

    private List<Map<String, Object>> lookup(Connection con, String sql) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", rs.getInt("id"));
                item.put("name", rs.getString("name"));
                list.add(item);
            }
        }
        return list;
    }

    /** uri.DBConnect trả null khi lỗi -> đổi thành exception để servlet báo lỗi rõ ràng. */
    private Connection connect() throws SQLException {
        Connection con = DBConnect.getConnection();
        if (con == null) {
            throw new SQLException("Không kết nối được database (xem log Tomcat)");
        }
        return con;
    }

    private static void setText(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) ps.setNull(index, Types.VARCHAR);
        else ps.setString(index, value.trim());
    }

    private static void setInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) ps.setNull(index, Types.INTEGER);
        else ps.setInt(index, value);
    }
}
