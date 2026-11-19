package dao.hieulnmth08470;

import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dao.hieulnmth08470.InventorySupport.exec;
import static dao.hieulnmth08470.InventorySupport.inTransaction;
import static dao.hieulnmth08470.InventorySupport.list;
import static dao.hieulnmth08470.InventorySupport.one;
import static dao.hieulnmth08470.InventorySupport.open;

/**
 * Tồn kho theo gói, theo lô, lịch sử biến động, xuất lô hết hạn và ngưỡng cảnh báo
 * — UC105..UC116 (Lê Nguyễn Minh Hiếu).
 * <p>Tồn kho không lưu thành cột: luôn tính từ sổ cái stock_movements qua view
 * v_lot_stock / v_package_stock. Câu SQL bám theo cột "Truy vấn SQL" trong file phân công.</p>
 */
public class InventoryDAO {

    /** Quyền xuất hủy, đặt ngưỡng, duyệt / từ chối kiểm kê và xem thông báo (chủ cửa hàng). */
    public static final String MANAGE_PERMISSION = "INVENTORY_MANAGE";
    private static final int MAX_HISTORY_ROWS = 500;

    // ================================================================== UC105, UC106, UC107

    /** Tồn theo gói (đã gắn trạng thái tồn) — dùng chung cho UC105 / UC106 / UC107 / UC115-B1. */
    private static final String PACKAGE_STOCK = """
            SELECT p.package_id, p.sku, pr.product_name, v.variant_name, p.weight_g, g.grind_name,
                   s.quantity_on_hand, p.min_stock_level,
                   CASE WHEN s.quantity_on_hand <= 0 THEN 'OUT_OF_STOCK'
                        WHEN s.is_low_stock THEN 'LOW_STOCK'
                        ELSE 'IN_STOCK' END AS stock_status,
                   (SELECT COUNT(*) FROM v_lot_stock ls
                     WHERE ls.package_id = p.package_id AND ls.quantity_on_hand <> 0) AS lot_count
            FROM v_package_stock s
            JOIN packages p ON p.package_id = s.package_id
            JOIN product_variants v ON v.variant_id = p.variant_id
            JOIN products pr ON pr.product_id = v.product_id
            JOIN grind_sizes g ON g.grind_size_id = p.grind_size_id
            WHERE p.status = 'ACTIVE'
            """;

    /**
     * UC105 xem tồn theo gói, UC106 tìm theo SKU / tên sản phẩm / tên biến thể (không dấu),
     * UC107 lọc theo trạng thái tồn. Từ khóa và trạng thái đều có thể bỏ trống.
     */
    public List<Map<String, Object>> packageStock(String keyword, String stockStatus) throws SQLException {
        String status = stockStatus == null ? null : stockStatus.strip().toUpperCase();
        if (status != null && !List.of("OUT_OF_STOCK", "LOW_STOCK", "IN_STOCK").contains(status)) {
            throw new IllegalArgumentException("Trạng thái tồn không hợp lệ");
        }
        String key = keyword == null || keyword.isBlank() ? null : keyword.strip();
        try (Connection connection = open()) {
            return list(connection, "SELECT * FROM (" + PACKAGE_STOCK + """
                      AND (CAST(? AS TEXT) IS NULL
                           OR p.sku ILIKE '%' || ? || '%'
                           OR f_search_key(pr.product_name) LIKE '%' || f_search_key(?) || '%'
                           OR f_search_key(v.variant_name) LIKE '%' || f_search_key(?) || '%')
                    ) t
                    WHERE (CAST(? AS TEXT) IS NULL OR t.stock_status = ?)
                    ORDER BY t.product_name, t.variant_name, t.weight_g
                    """, key, key, key, key, status, status);
        }
    }

    /** Đếm số gói theo từng trạng thái tồn (ô thống kê đầu trang). */
    public Map<String, Object> packageSummary() throws SQLException {
        try (Connection connection = open()) {
            return one(connection, """
                    SELECT COUNT(*) AS total,
                           COUNT(*) FILTER (WHERE stock_status = 'OUT_OF_STOCK') AS out_of_stock,
                           COUNT(*) FILTER (WHERE stock_status = 'LOW_STOCK') AS low_stock,
                           COUNT(*) FILTER (WHERE stock_status = 'IN_STOCK') AS in_stock
                    FROM (""" + PACKAGE_STOCK + ") t");
        }
    }

    // ================================================================== UC108, UC109, UC110

    /**
     * UC108 danh sách lô còn tồn, UC109 lọc theo hạn sử dụng (EXPIRED / NEAR_EXPIRY / VALID)
     * và khoảng ngày hết hạn. Thêm lọc theo gói và từ khóa (mã lô / SKU) để mở từ trang tồn theo gói.
     */
    public List<Map<String, Object>> lots(String expiryStatus, LocalDate from, LocalDate to,
                                          Integer packageId, String keyword) throws SQLException {
        String status = expiryStatus == null ? null : expiryStatus.strip().toUpperCase();
        if (status != null && !List.of("EXPIRED", "NEAR_EXPIRY", "VALID").contains(status)) {
            throw new IllegalArgumentException("Trạng thái hạn sử dụng không hợp lệ");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Từ ngày phải trước hoặc bằng đến ngày");
        }
        String key = keyword == null || keyword.isBlank() ? null : keyword.strip();
        try (Connection connection = open()) {
            return list(connection, """
                    SELECT * FROM (
                      SELECT s.lot_id, s.lot_code, s.package_id, p.sku, s.roast_date, s.expiry_date,
                             s.quantity_on_hand,
                             s.expiry_date - CURRENT_DATE AS days_left,
                             CASE WHEN s.expiry_date < CURRENT_DATE THEN 'EXPIRED'
                                  WHEN s.expiry_date <= CURRENT_DATE + CAST(st.setting_value AS INT) THEN 'NEAR_EXPIRY'
                                  ELSE 'VALID' END AS expiry_status
                      FROM v_lot_stock s
                      JOIN packages p ON p.package_id = s.package_id
                      CROSS JOIN (SELECT setting_value FROM system_settings
                                  WHERE setting_key = 'inventory.near_expiry_days') st
                      WHERE s.quantity_on_hand <> 0
                    ) t
                    WHERE (CAST(? AS TEXT) IS NULL OR t.expiry_status = ?)
                      AND (CAST(? AS DATE) IS NULL OR t.expiry_date >= CAST(? AS DATE))
                      AND (CAST(? AS DATE) IS NULL OR t.expiry_date <= CAST(? AS DATE))
                      AND (CAST(? AS INT) IS NULL OR t.package_id = CAST(? AS INT))
                      AND (CAST(? AS TEXT) IS NULL OR t.lot_code ILIKE '%' || ? || '%' OR t.sku ILIKE '%' || ? || '%')
                    ORDER BY t.expiry_date, t.lot_id
                    """, status, status, from, from, to, to, packageId, packageId, key, key, key);
        }
    }

    /** UC110 chi tiết lô: mã lô, SKU, ngày rang, hạn, giá nhập, SL nhập, tồn hiện tại, hóa đơn nhập. */
    public Map<String, Object> lotDetail(long lotId) throws SQLException {
        try (Connection connection = open()) {
            return one(connection, """
                    SELECT s.lot_id, s.lot_code, s.package_id, p.sku, pr.product_name, v.variant_name, p.weight_g,
                           s.roast_date, s.expiry_date, s.unit_cost,
                           s.expiry_date - CURRENT_DATE AS days_left,
                           pil.quantity AS imported_qty, s.quantity_on_hand,
                           pi.invoice_no, pi.invoice_date, pi.supplier_id, sp.supplier_name
                    FROM v_lot_stock s
                    JOIN lots l ON l.lot_id = s.lot_id
                    JOIN purchase_invoice_lines pil ON pil.purchase_line_id = l.purchase_line_id
                    JOIN purchase_invoices pi ON pi.purchase_invoice_id = pil.purchase_invoice_id
                    LEFT JOIN suppliers sp ON sp.supplier_id = pi.supplier_id
                    JOIN packages p ON p.package_id = s.package_id
                    JOIN product_variants v ON v.variant_id = p.variant_id
                    JOIN products pr ON pr.product_id = v.product_id
                    WHERE s.lot_id = ?
                    """, lotId);
        }
    }

    // ================================================================== UC111

    /**
     * UC111 lịch sử biến động tồn: lọc theo lô, gói, khoảng ngày (đều có thể bỏ trống).
     * balance_after = tồn của lô sau dòng đó (cộng dồn theo thời gian). Tối đa 500 dòng mới nhất.
     */
    public List<Map<String, Object>> movements(Long lotId, Integer packageId, LocalDate from, LocalDate to)
            throws SQLException {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Từ ngày phải trước hoặc bằng đến ngày");
        }
        try (Connection connection = open()) {
            return list(connection, """
                    SELECT * FROM (
                      SELECT m.movement_id, m.occurred_at, l.lot_code, pil.package_id, p.sku,
                             m.movement_type, m.quantity_change,
                             SUM(m.quantity_change) OVER (PARTITION BY m.lot_id
                                 ORDER BY m.occurred_at, m.movement_id) AS balance_after,
                             si.invoice_no, m.disposal_id, m.stock_count_id,
                             u.full_name AS performed_by_name, m.lot_id
                      FROM stock_movements m
                      JOIN lots l ON l.lot_id = m.lot_id
                      JOIN purchase_invoice_lines pil ON pil.purchase_line_id = l.purchase_line_id
                      JOIN packages p ON p.package_id = pil.package_id
                      LEFT JOIN sales_invoices si ON si.sales_invoice_id = m.sales_invoice_id
                      LEFT JOIN users u ON u.user_id = m.performed_by
                    ) t
                    WHERE (CAST(? AS BIGINT) IS NULL OR t.lot_id = CAST(? AS BIGINT))
                      AND (CAST(? AS INT) IS NULL OR t.package_id = CAST(? AS INT))
                      AND (CAST(? AS DATE) IS NULL OR t.occurred_at >= CAST(? AS DATE))
                      AND (CAST(? AS DATE) IS NULL OR t.occurred_at < CAST(? AS DATE) + 1)
                    ORDER BY t.occurred_at DESC, t.movement_id DESC
                    LIMIT ?
                    """, lotId, lotId, packageId, packageId, from, from, to, to, MAX_HISTORY_ROWS);
        }
    }

    // ================================================================== UC112, UC114

    /** UC112-B1: lô đã hết hạn nhưng còn tồn (chờ xuất hủy). */
    public List<Map<String, Object>> expiredLots() throws SQLException {
        try (Connection connection = open()) {
            return list(connection, """
                    SELECT s.lot_id, s.lot_code, p.sku, s.expiry_date, s.quantity_on_hand, s.unit_cost,
                           s.quantity_on_hand * s.unit_cost AS loss_amount,
                           CURRENT_DATE - s.expiry_date AS days_over
                    FROM v_lot_stock s
                    JOIN packages p ON p.package_id = s.package_id
                    WHERE s.expiry_date < CURRENT_DATE AND s.quantity_on_hand > 0
                    ORDER BY s.expiry_date
                    """);
        }
    }

    /**
     * UC112-B2: tạo 1 phiếu xuất hủy (lý do EXPIRED) cho các lô đã chọn, xuất hết tồn của từng lô
     * và ghi sổ DISPOSAL. Lô chưa hết hạn hoặc đã hết tồn sẽ bị bỏ qua.
     * @return disposalId, lineCount, quantity
     */
    public Map<String, Object> disposeExpired(List<Long> lotIds, String noteInput, int userId)
            throws SQLException {
        if (lotIds == null || lotIds.isEmpty()) {
            throw new IllegalArgumentException("Chọn ít nhất một lô hết hạn");
        }
        String note = InventorySupport.note(noteInput, "Ghi chú", 500);
        try (Connection connection = open()) {
            return inTransaction(connection, c -> {
                Map<String, Object> reason = one(c,
                        "SELECT disposal_reason_id FROM disposal_reasons WHERE reason_code = 'EXPIRED'");
                if (reason == null) {
                    throw new IllegalStateException("Chưa có lý do xuất hủy EXPIRED trong bảng disposal_reasons");
                }
                Array ids = c.createArrayOf("bigint", lotIds.toArray(new Long[0]));
                List<Map<String, Object>> rows = list(c, """
                        WITH x AS (
                          SELECT s.lot_id, CAST(s.quantity_on_hand AS INT) AS qty
                          FROM v_lot_stock s
                          WHERE s.lot_id = ANY (CAST(? AS BIGINT[]))
                            AND s.expiry_date < CURRENT_DATE AND s.quantity_on_hand > 0
                        ), d AS (
                          INSERT INTO disposals (created_by, disposal_reason_id, note)
                          SELECT ?, ?, ?
                          WHERE EXISTS (SELECT 1 FROM x)
                          RETURNING disposal_id
                        ), dl AS (
                          INSERT INTO disposal_lines (disposal_id, lot_id, quantity)
                          SELECT d.disposal_id, x.lot_id, x.qty FROM d, x
                          RETURNING disposal_id, lot_id, quantity
                        )
                        INSERT INTO stock_movements (lot_id, movement_type, quantity_change, disposal_id, performed_by)
                        SELECT lot_id, 'DISPOSAL', -quantity, disposal_id, ? FROM dl
                        RETURNING disposal_id, lot_id, quantity_change
                        """, ids, userId, reason.get("disposalReasonId"), note, userId);
                if (rows.isEmpty()) {
                    throw new IllegalStateException("Các lô đã chọn không còn hết hạn hoặc đã hết tồn");
                }
                long quantity = 0;
                for (Map<String, Object> row : rows) {
                    quantity -= ((Number) row.get("quantityChange")).longValue();
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("disposalId", rows.get(0).get("disposalId"));
                result.put("lineCount", rows.size());
                result.put("quantity", quantity);
                return result;
            });
        }
    }

    /** UC114 danh sách phiếu xuất hủy + giá trị lỗ (SL hủy x giá nhập). */
    public List<Map<String, Object>> disposals(LocalDate from, LocalDate to) throws SQLException {
        try (Connection connection = open()) {
            return list(connection, """
                    SELECT d.disposal_id, d.disposed_at, r.reason_name, d.note, d.created_by,
                           u.full_name AS created_by_name, lo.loss_amount,
                           (SELECT COUNT(*) FROM disposal_lines dl WHERE dl.disposal_id = d.disposal_id) AS line_count,
                           (SELECT SUM(dl.quantity) FROM disposal_lines dl WHERE dl.disposal_id = d.disposal_id) AS quantity
                    FROM disposals d
                    JOIN disposal_reasons r ON r.disposal_reason_id = d.disposal_reason_id
                    LEFT JOIN users u ON u.user_id = d.created_by
                    LEFT JOIN v_disposal_losses lo ON lo.disposal_id = d.disposal_id
                    WHERE (CAST(? AS DATE) IS NULL OR d.disposed_at >= CAST(? AS DATE))
                      AND (CAST(? AS DATE) IS NULL OR d.disposed_at < CAST(? AS DATE) + 1)
                    ORDER BY d.disposed_at DESC
                    """, from, from, to, to);
        }
    }

    /** Các lô trong 1 phiếu xuất hủy (mở rộng dòng ở UC114). */
    public List<Map<String, Object>> disposalLines(long disposalId) throws SQLException {
        try (Connection connection = open()) {
            return list(connection, """
                    SELECT dl.disposal_line_id, l.lot_code, p.sku, l.expiry_date, dl.quantity, pil.unit_cost,
                           dl.quantity * pil.unit_cost AS loss_amount
                    FROM disposal_lines dl
                    JOIN lots l ON l.lot_id = dl.lot_id
                    JOIN purchase_invoice_lines pil ON pil.purchase_line_id = l.purchase_line_id
                    JOIN packages p ON p.package_id = pil.package_id
                    WHERE dl.disposal_id = ?
                    ORDER BY l.lot_code
                    """, disposalId);
        }
    }

    // ================================================================== UC115, UC116

    /** UC115-B2: đặt ngưỡng tồn thấp cho 1 gói (>= 0). */
    public boolean updateMinStock(int packageId, Integer minStockLevel) throws SQLException {
        int level = InventorySupport.nonNegative(minStockLevel, "Ngưỡng tồn thấp");
        try (Connection connection = open()) {
            return exec(connection, """
                    UPDATE packages
                    SET min_stock_level = ?
                    WHERE package_id = ?
                    """, level, packageId) == 1;
        }
    }

    /** UC116-B1: số ngày cận hạn hiện tại + ai sửa lần cuối. */
    public Map<String, Object> nearExpirySetting() throws SQLException {
        try (Connection connection = open()) {
            return one(connection, """
                    SELECT CAST(s.setting_value AS INT) AS near_expiry_days, s.updated_at, u.full_name AS updated_by_name
                    FROM system_settings s
                    LEFT JOIN users u ON u.user_id = s.updated_by
                    WHERE s.setting_key = 'inventory.near_expiry_days'
                    """);
        }
    }

    /** UC116-B2: cập nhật số ngày cận hạn (1..365). */
    public boolean updateNearExpiry(Integer days, int userId) throws SQLException {
        if (days == null) {
            throw new IllegalArgumentException("Nhập số ngày cận hạn");
        }
        if (days <= 0 || days > 365) {
            throw new IllegalArgumentException("Số ngày cận hạn phải từ 1 đến 365");
        }
        try (Connection connection = open()) {
            return exec(connection, """
                    UPDATE system_settings
                    SET setting_value = CAST(CAST(? AS INT) AS TEXT),
                        updated_by = ?,
                        updated_at = now()
                    WHERE setting_key = 'inventory.near_expiry_days'
                      AND CAST(? AS INT) > 0
                    """, days, userId, days) == 1;
        }
    }
}
