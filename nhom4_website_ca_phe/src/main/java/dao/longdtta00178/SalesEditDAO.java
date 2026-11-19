package dao.longdtta00178;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uri.DBConnect;

/**
 * [155] SỬA ĐƠN ĐÃ XÁC NHẬN (bán theo mã lô).
 *
 * Quyền: có SALES_INVOICE_EDIT (chủ) -> sửa mọi đơn đã xác nhận;
 *        không có -> chỉ đơn mình lập, trong EDIT_MINUTES phút sau khi xác nhận.
 * Kho:   giảm túi ở lô nào thì trả về đúng lô đó (158.2, SALE_RETURN);
 *        tăng / thêm lô thì trừ đúng lô đó (SALE). Phần vượt tồn chỉ cho khi quét QR (bán âm, chủ duyệt sau).
 * Tiền:  tổng mới > đã thu -> thu tiền mặt ngay; tổng mới < đã thu -> luôn hoàn tiền mặt (157, REFUND).
 * Tất cả trong 1 transaction, ghi nhật ký EDIT_PAID_INVOICE (trước / sau).
 */
public class SalesEditDAO {

    public static final int EDIT_MINUTES = 10;
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private final SalesLotDAO lotDao = new SalesLotDAO();

    /** 1 dòng trong đơn sau khi sửa: lô + số túi + số túi quét thêm trong lần sửa này */
    public record LotQty(long lotId, int quantity, int scannedAdded) {}

    /* =========================================================
       QUYỀN SỬA: {canEdit, editReason (lý do không được), editSecondsLeft (null = không giới hạn)}
       ========================================================= */
    public Map<String, Object> editPermission(long invoiceId, int userId, boolean isOwner) throws SQLException {
        try (Connection con = connect()) {
            return editPermission(con, invoiceId, userId, isOwner);
        }
    }

    private Map<String, Object> editPermission(Connection con, long invoiceId, int userId, boolean isOwner)
            throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT status, created_by, confirmed_at,
                       EXTRACT(EPOCH FROM (confirmed_at + make_interval(mins => ?) - now()))::int AS seconds_left
                FROM sales_invoices WHERE sales_invoice_id = ?
                """)) {
            ps.setInt(1, EDIT_MINUTES);
            ps.setLong(2, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy đơn");
                String reason = null;
                Integer secondsLeft = null;
                if (!"PAID".equals(rs.getString("status"))) {
                    reason = "Chỉ sửa được đơn đã xác nhận";
                } else if (!isOwner) {
                    if (rs.getInt("created_by") != userId) {
                        reason = "Nhân viên chỉ sửa được đơn do mình lập";
                    } else if (rs.getObject("confirmed_at") == null || rs.getInt("seconds_left") <= 0) {
                        reason = "Đã quá " + EDIT_MINUTES + " phút sau khi xác nhận, báo chủ cửa hàng sửa";
                    } else {
                        secondsLeft = rs.getInt("seconds_left");
                    }
                }
                result.put("canEdit", reason == null);
                result.put("editReason", reason);
                result.put("editSecondsLeft", secondsLeft);       // null = chủ, không giới hạn
            }
        }
        return result;
    }

    /* =========================================================
       TRA MÃ LÔ (quét / gõ khi sửa đơn): thông tin để thêm vào bản nháp
       ========================================================= */
    public Map<String, Object> findLot(String lotCode) throws SQLException {
        String sql = """
                SELECT lo.lot_id, lo.lot_code, lo.expiry_date, pil.package_id, pk.sku, pk.weight_g,
                       v.variant_name, g.is_whole_bean, cp.sale_price,
                       pk.status = 'ACTIVE' AND v.status = 'ACTIVE' AS active,
                       (SELECT COALESCE(SUM(m.quantity_change), 0) FROM stock_movements m
                        WHERE m.lot_id = lo.lot_id) AS stock
                FROM lots lo
                JOIN purchase_invoice_lines pil ON pil.purchase_line_id = lo.purchase_line_id
                JOIN packages pk                ON pk.package_id = pil.package_id
                JOIN product_variants v         ON v.variant_id = pk.variant_id
                JOIN grind_sizes g              ON g.grind_size_id = pk.grind_size_id
                LEFT JOIN v_package_current_price cp ON cp.package_id = pk.package_id
                WHERE upper(lo.lot_code) = upper(?)
                """;
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, lotCode == null ? "" : lotCode.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy lô " + lotCode);
                LocalDate expiry = rs.getObject("expiry_date", LocalDate.class);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("lotId", rs.getLong("lot_id"));
                m.put("lotCode", rs.getString("lot_code"));
                m.put("expiryDate", expiry);
                m.put("expired", expiry.isBefore(LocalDate.now(VIETNAM)));
                m.put("packageId", rs.getInt("package_id"));
                m.put("sku", rs.getString("sku"));
                m.put("weightG", rs.getInt("weight_g"));
                m.put("variantName", rs.getString("variant_name"));
                m.put("wholeBean", rs.getBoolean("is_whole_bean"));
                m.put("salePrice", rs.getBigDecimal("sale_price"));
                m.put("active", rs.getBoolean("active"));
                m.put("stock", rs.getInt("stock"));
                return m;
            }
        }
    }

    /* =========================================================
       [155] LƯU SỬA ĐƠN
       target: toàn bộ lô của đơn SAU khi sửa (lô không có trong danh sách = bỏ hết)
       cashGiven: tiền khách đưa khi phải thu thêm
       Trả về: {totalBefore, totalAfter, charged, refunded}
       ========================================================= */
    public Map<String, Object> saveEdit(long invoiceId, int userId, boolean isOwner, List<LotQty> target,
                                        BigDecimal cashGiven, String reason) throws SQLException {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Nhập lý do sửa đơn");
        if (target.stream().noneMatch(t -> t.quantity() > 0)) {
            throw new IllegalArgumentException("Đơn không còn gói nào. Muốn bỏ hết thì dùng Hủy đơn.");
        }

        try (Connection con = connect()) {
            con.setAutoCommit(false);
            try {
                // 1. Khóa đơn + kiểm quyền
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT status FROM sales_invoices WHERE sales_invoice_id = ? FOR UPDATE")) {
                    ps.setLong(1, invoiceId);
                    ps.executeQuery().close();
                }
                Map<String, Object> perm = editPermission(con, invoiceId, userId, isOwner);
                if (!(Boolean) perm.get("canEdit")) throw new IllegalArgumentException((String) perm.get("editReason"));

                BigDecimal totalBefore = total(con, invoiceId);
                String before = snapshot(con, invoiceId);

                // 2. Lô hiện tại của đơn: lotId -> {lineId, quantity}
                Map<Long, long[]> current = new LinkedHashMap<>();
                try (PreparedStatement ps = con.prepareStatement("""
                        SELECT a.lot_id, a.sales_line_id, a.quantity
                        FROM sales_line_allocations a
                        JOIN sales_invoice_lines l ON l.sales_line_id = a.sales_line_id
                        WHERE l.sales_invoice_id = ?
                        """)) {
                    ps.setLong(1, invoiceId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            current.put(rs.getLong(1), new long[] {rs.getLong(2), rs.getLong(3)});
                        }
                    }
                }

                // 3. Gom yêu cầu theo lô (lô bị bỏ -> 0)
                Map<Long, LotQty> wanted = new LinkedHashMap<>();
                for (LotQty t : target) {
                    if (t.quantity() < 0 || t.scannedAdded() < 0) throw new IllegalArgumentException("Số lượng không hợp lệ");
                    wanted.put(t.lotId(), t);
                }
                for (Long lotId : current.keySet()) wanted.putIfAbsent(lotId, new LotQty(lotId, 0, 0));

                // 4. Áp từng lô (thứ tự lot_id để không khóa chéo)
                Set<Long> negativeLots = new LinkedHashSet<>();
                List<Long> lotIds = new ArrayList<>(wanted.keySet());
                lotIds.sort(null);
                LocalDate today = LocalDate.now(VIETNAM);
                for (Long lotId : lotIds) {
                    LotQty t = wanted.get(lotId);
                    long[] cur = current.get(lotId);
                    int oldQty = cur == null ? 0 : (int) cur[1];
                    int delta = t.quantity() - oldQty;
                    if (delta == 0) continue;

                    // Thông tin lô (khóa lô)
                    int packageId;
                    String code;
                    LocalDate expiry;
                    BigDecimal price;
                    boolean active;
                    try (PreparedStatement ps = con.prepareStatement("""
                            SELECT lo.lot_code, lo.expiry_date, pil.package_id, cp.sale_price,
                                   pk.status = 'ACTIVE' AND v.status = 'ACTIVE' AS active
                            FROM lots lo
                            JOIN purchase_invoice_lines pil ON pil.purchase_line_id = lo.purchase_line_id
                            JOIN packages pk                ON pk.package_id = pil.package_id
                            JOIN product_variants v         ON v.variant_id = pk.variant_id
                            LEFT JOIN v_package_current_price cp ON cp.package_id = pk.package_id
                            WHERE lo.lot_id = ?
                            FOR UPDATE OF lo
                            """)) {
                        ps.setLong(1, lotId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy lô #" + lotId);
                            code = rs.getString("lot_code");
                            expiry = rs.getObject("expiry_date", LocalDate.class);
                            packageId = rs.getInt("package_id");
                            price = rs.getBigDecimal("sale_price");
                            active = rs.getBoolean("active");
                        }
                    }

                    if (delta < 0) {
                        // 158.2 trả túi về đúng lô
                        movement(con, lotId, "SALE_RETURN", -delta, invoiceId, userId);
                    } else {
                        if (expiry.isBefore(today)) throw new IllegalArgumentException("Lô " + code + " đã hết hạn, không bán thêm");
                        if (!active) throw new IllegalArgumentException("Lô " + code + " thuộc gói đã ngừng bán");
                        int stock = lotStock(con, lotId);
                        int over = delta - Math.max(stock, 0);
                        if (over > 0) {
                            if (over > t.scannedAdded()) {
                                throw new IllegalArgumentException("Lô " + code + " chỉ còn " + Math.max(stock, 0)
                                        + " túi. Quét QR trên túi nếu có hàng thật.");
                            }
                            negativeLots.add(lotId);
                            try (PreparedStatement ps = con.prepareStatement(
                                    "SELECT set_config('app.allow_negative_lots', ?, true)")) {
                                ps.setString(1, "," + lotId + ",");
                                ps.executeQuery().close();
                            }
                        }
                        try {
                            movement(con, lotId, "SALE", -delta, invoiceId, userId);
                        } catch (SQLException e) {
                            if (e.getMessage() != null && e.getMessage().contains("không đủ tồn")) {
                                throw new IllegalArgumentException("Lô " + code + " không đủ tồn");
                            }
                            throw e;
                        }
                    }

                    // Dòng hàng của gói (gói mới thì lấy giá hiện hành)
                    long lineId;
                    if (cur != null) {
                        lineId = cur[0];
                    } else {
                        if (price == null) throw new IllegalArgumentException("Gói của lô " + code + " chưa có giá bán");
                        try (PreparedStatement ps = con.prepareStatement("""
                                INSERT INTO sales_invoice_lines (sales_invoice_id, package_id, quantity, unit_price)
                                VALUES (?, ?, ?, ?)
                                ON CONFLICT (sales_invoice_id, package_id) DO UPDATE SET quantity = sales_invoice_lines.quantity
                                RETURNING sales_line_id
                                """)) {                                  // số túi tính lại ở bước 5
                            ps.setLong(1, invoiceId);
                            ps.setInt(2, packageId);
                            ps.setInt(3, t.quantity());
                            ps.setBigDecimal(4, price);
                            try (ResultSet rs = ps.executeQuery()) {
                                rs.next();
                                lineId = rs.getLong(1);
                            }
                        }
                    }

                    // Lô của dòng
                    if (t.quantity() == 0) {
                        exec(con, "DELETE FROM sales_line_allocations WHERE sales_line_id = ? AND lot_id = ?", lineId, lotId);
                    } else {
                        try (PreparedStatement ps = con.prepareStatement("""
                                INSERT INTO sales_line_allocations (sales_line_id, lot_id, quantity, scanned_qty)
                                VALUES (?, ?, ?, LEAST(?, ?))
                                ON CONFLICT (sales_line_id, lot_id) DO UPDATE
                                SET quantity = EXCLUDED.quantity,
                                    scanned_qty = LEAST(sales_line_allocations.scanned_qty + ?, EXCLUDED.quantity)
                                """)) {
                            ps.setLong(1, lineId);
                            ps.setLong(2, lotId);
                            ps.setInt(3, t.quantity());
                            ps.setInt(4, t.scannedAdded());
                            ps.setInt(5, t.quantity());
                            ps.setInt(6, t.scannedAdded());
                            ps.executeUpdate();
                        }
                    }
                }
                exec(con, "SELECT set_config('app.allow_negative_lots', '', true)");

                // 5. Số túi mỗi dòng = tổng lô; dòng hết lô thì xóa
                exec(con, """
                        UPDATE sales_invoice_lines l
                        SET quantity = s.qty
                        FROM (SELECT a.sales_line_id, SUM(a.quantity) AS qty
                              FROM sales_line_allocations a
                              JOIN sales_invoice_lines l2 ON l2.sales_line_id = a.sales_line_id
                              WHERE l2.sales_invoice_id = ?
                              GROUP BY a.sales_line_id) s
                        WHERE s.sales_line_id = l.sales_line_id
                        """, invoiceId);
                exec(con, """
                        DELETE FROM sales_invoice_lines l
                        WHERE l.sales_invoice_id = ?
                          AND NOT EXISTS (SELECT 1 FROM sales_line_allocations a WHERE a.sales_line_id = l.sales_line_id)
                        """, invoiceId);

                // 6. Lô bị âm -> phiếu điều chỉnh chờ chủ duyệt
                for (Long lotId : negativeLots) lotDao.requestNegativeAdjustment(con, invoiceId, lotId, userId);

                // 7. Tiền: thu thêm tiền mặt / hoàn tiền mặt
                BigDecimal totalAfter = total(con, invoiceId);
                BigDecimal due = totalAfter.subtract(paid(con, invoiceId));
                BigDecimal charged = BigDecimal.ZERO, refunded = BigDecimal.ZERO;
                if (due.signum() > 0) {
                    if (cashGiven == null || cashGiven.compareTo(due) < 0) {
                        throw new IllegalArgumentException("Phải thu thêm " + money(due) + " tiền mặt, khách đưa chưa đủ");
                    }
                    payment(con, invoiceId, due, "CHARGE", userId);
                    charged = due;
                } else if (due.signum() < 0) {
                    refunded = due.negate();
                    payment(con, invoiceId, refunded, "REFUND", userId);
                }

                // 8. Nhật ký trước / sau
                try (PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO audit_logs (user_id, action, entity_type, entity_id, detail)
                        VALUES (?, 'EDIT_PAID_INVOICE', 'sales_invoices', ?,
                                jsonb_build_object('reason', CAST(? AS TEXT),
                                                   'before', CAST(? AS JSONB), 'after', CAST(? AS JSONB),
                                                   'totalBefore', ?, 'totalAfter', ?,
                                                   'charged', ?, 'refunded', ?))
                        """)) {
                    ps.setInt(1, userId);
                    ps.setLong(2, invoiceId);
                    ps.setString(3, reason.trim());
                    ps.setString(4, before);
                    ps.setString(5, snapshot(con, invoiceId));
                    ps.setBigDecimal(6, totalBefore);
                    ps.setBigDecimal(7, totalAfter);
                    ps.setBigDecimal(8, charged);
                    ps.setBigDecimal(9, refunded);
                    ps.executeUpdate();
                }

                con.commit();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("totalBefore", totalBefore);
                result.put("totalAfter", totalAfter);
                result.put("charged", charged);
                result.put("refunded", refunded);
                return result;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /* ---------- Hàm phụ ---------- */

    /** [{"lot":"L...","qty":2,"price":230000},...] để ghi nhật ký */
    private String snapshot(Connection con, long invoiceId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT COALESCE(jsonb_agg(jsonb_build_object('lot', lo.lot_code, 'qty', a.quantity, 'price', l.unit_price)
                                          ORDER BY lo.lot_code), '[]'::jsonb)::text
                FROM sales_line_allocations a
                JOIN sales_invoice_lines l ON l.sales_line_id = a.sales_line_id
                JOIN lots lo               ON lo.lot_id = a.lot_id
                WHERE l.sales_invoice_id = ?
                """)) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private void movement(Connection con, long lotId, String type, int change, long invoiceId, int userId)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                INSERT INTO stock_movements (lot_id, movement_type, quantity_change, sales_invoice_id, performed_by)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            ps.setLong(1, lotId);
            ps.setString(2, type);
            ps.setInt(3, change);
            ps.setLong(4, invoiceId);
            ps.setInt(5, userId);
            ps.executeUpdate();
        }
    }

    private void payment(Connection con, long invoiceId, BigDecimal amount, String kind, int userId)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                INSERT INTO payments (sales_invoice_id, payment_method_id, amount, status, payment_kind, confirmed_by, paid_at)
                VALUES (?, (SELECT payment_method_id FROM payment_methods WHERE method_code = 'CASH'),
                        ?, 'SUCCESS', ?, ?, now())
                """)) {
            ps.setLong(1, invoiceId);
            ps.setBigDecimal(2, amount);
            ps.setString(3, kind);
            ps.setInt(4, userId);
            ps.executeUpdate();
        }
    }

    private BigDecimal total(Connection con, long invoiceId) throws SQLException {
        return decimal(con, "SELECT COALESCE(SUM(quantity * unit_price), 0) FROM sales_invoice_lines WHERE sales_invoice_id = ?",
                invoiceId);
    }

    private BigDecimal paid(Connection con, long invoiceId) throws SQLException {
        return decimal(con, """
                SELECT COALESCE(SUM(CASE WHEN payment_kind = 'CHARGE' THEN amount ELSE -amount END), 0)
                FROM payments WHERE sales_invoice_id = ? AND status = 'SUCCESS'
                """, invoiceId);
    }

    private BigDecimal decimal(Connection con, String sql, long id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private int lotStock(Connection con, long lotId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COALESCE(SUM(quantity_change), 0) FROM stock_movements WHERE lot_id = ?")) {
            ps.setLong(1, lotId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int exec(Connection con, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            if (sql.trim().toUpperCase().startsWith("SELECT")) {
                ps.executeQuery().close();
                return 0;
            }
            return ps.executeUpdate();
        }
    }

    private static String money(BigDecimal v) {
        return String.format("%,.0fđ", v).replace(',', '.');
    }

    private Connection connect() throws SQLException {
        Connection con = DBConnect.getConnection();
        if (con == null) throw new SQLException("Không kết nối được database (xem log Tomcat)");
        return con;
    }
}