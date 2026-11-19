package dao.longdtta00178;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import uri.DBConnect;

/**
 * Trang hóa đơn (longdtta001782): tra cứu, chi tiết, in lại, hủy đơn đã xác nhận.
 * 147 in lại, 148–152 danh sách + lọc, 153 chi tiết (dùng lại SalesOrderDAO),
 * 154 hủy đơn đã xác nhận, 156/158 hoàn kho đúng lô, 157 trừ tiền (khoản REFUND tiền mặt).
 */
public class SalesInvoiceDAO {

    public static final int PAGE_SIZE = 15;

    /* =========================================================
       [148–152] DANH SÁCH ĐƠN + TÌM KIẾM + LỌC
       Tham số null = không lọc. fromDate / toDate dạng yyyy-MM-dd (giờ VN).
       Trả về: {total, page, pageSize, rows:[{...}]}
       ========================================================= */
    public Map<String, Object> search(String keyword, String fromDate, String toDate,
                                      Integer methodId, String status, int page) throws SQLException {
        String sql = """
                SELECT si.sales_invoice_id, si.invoice_no, si.created_at, si.status, si.is_restocked,
                       u.full_name AS cashier_name,
                       (SELECT COALESCE(SUM(l.quantity * l.unit_price), 0)
                        FROM sales_invoice_lines l
                        WHERE l.sales_invoice_id = si.sales_invoice_id) AS total_amount,
                       (SELECT string_agg(DISTINCT pm.method_name, ' + ')
                        FROM payments p JOIN payment_methods pm ON pm.payment_method_id = p.payment_method_id
                        WHERE p.sales_invoice_id = si.sales_invoice_id
                          AND p.status = 'SUCCESS' AND p.payment_kind = 'CHARGE') AS payment_methods,
                       EXISTS (SELECT 1 FROM payments p
                               WHERE p.sales_invoice_id = si.sales_invoice_id
                                 AND p.status = 'SUCCESS' AND p.payment_kind = 'REFUND') AS refunded,
                       COUNT(*) OVER () AS total_rows
                FROM sales_invoices si
                JOIN users u ON u.user_id = si.created_by
                WHERE (CAST(? AS TEXT) IS NULL OR si.invoice_no ILIKE '%' || CAST(? AS TEXT) || '%')      -- 149
                  AND (CAST(? AS DATE) IS NULL
                       OR si.created_at >= CAST(? AS DATE)::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh')       -- 150
                  AND (CAST(? AS DATE) IS NULL
                       OR si.created_at <  (CAST(? AS DATE) + 1)::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh') -- 150
                  AND (CAST(? AS INT) IS NULL OR EXISTS (                                                      -- 151
                         SELECT 1 FROM payments p
                         WHERE p.sales_invoice_id = si.sales_invoice_id AND p.status = 'SUCCESS'
                           AND p.payment_kind = 'CHARGE' AND p.payment_method_id = CAST(? AS INT)))
                  AND (CAST(? AS TEXT) IS NULL OR si.status = CAST(? AS TEXT))                               -- 152
                ORDER BY si.created_at DESC, si.sales_invoice_id DESC
                LIMIT ? OFFSET ?
                """;
        String method = methodId == null ? null : methodId.toString();
        List<Map<String, Object>> rows = new ArrayList<>();
        int total = 0;
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, keyword);
            ps.setString(2, keyword);
            ps.setString(3, fromDate);
            ps.setString(4, fromDate);
            ps.setString(5, toDate);
            ps.setString(6, toDate);
            ps.setString(7, method);
            ps.setString(8, method);
            ps.setString(9, status);
            ps.setString(10, status);
            ps.setInt(11, PAGE_SIZE);
            ps.setInt(12, (page - 1) * PAGE_SIZE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    total = rs.getInt("total_rows");
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("salesInvoiceId", rs.getLong("sales_invoice_id"));
                    row.put("invoiceNo", rs.getString("invoice_no"));
                    row.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
                    row.put("status", rs.getString("status"));
                    row.put("restocked", rs.getObject("is_restocked"));      // null | true | false
                    row.put("refunded", rs.getBoolean("refunded"));
                    row.put("cashierName", rs.getString("cashier_name"));
                    row.put("totalAmount", rs.getBigDecimal("total_amount"));
                    row.put("paymentMethods", rs.getString("payment_methods"));
                    rows.add(row);
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", PAGE_SIZE);
        result.put("rows", rows);
        return result;
    }

    /** Ô lọc "Thanh toán" (151) */
    public List<Map<String, Object>> findPaymentMethods() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT payment_method_id, method_name FROM payment_methods ORDER BY payment_method_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getInt(1));
                m.put("name", rs.getString(2));
                list.add(m);
            }
        }
        return list;
    }

    /* =========================================================
       [153] THÔNG TIN HỦY + SỐ LẦN IN LẠI (phần còn lại của chi tiết dùng SalesOrderDAO)
       ========================================================= */
    public Map<String, Object> findExtra(long invoiceId) throws SQLException {
        String sql = """
                SELECT si.cancelled_at, si.cancel_reason, si.is_restocked, cu.full_name AS cancelled_by_name,
                       (SELECT COUNT(*) FROM audit_logs a
                        WHERE a.entity_type = 'sales_invoices' AND a.entity_id = si.sales_invoice_id
                          AND a.action = 'REPRINT_INVOICE') AS reprint_count,
                       (SELECT COALESCE(SUM(a.quantity), 0) FROM sales_line_allocations a
                        JOIN sales_invoice_lines l ON l.sales_line_id = a.sales_line_id
                        WHERE l.sales_invoice_id = si.sales_invoice_id) AS allocated_qty
                FROM sales_invoices si
                LEFT JOIN users cu ON cu.user_id = si.cancelled_by
                WHERE si.sales_invoice_id = ?
                """;
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Object> m = new LinkedHashMap<>();
                if (!rs.next()) return m;
                m.put("cancelledAt", rs.getObject("cancelled_at", OffsetDateTime.class));
                m.put("cancelReason", rs.getString("cancel_reason"));
                m.put("restocked", rs.getObject("is_restocked"));
                m.put("cancelledByName", rs.getString("cancelled_by_name"));
                m.put("reprintCount", rs.getInt("reprint_count"));
                m.put("allocatedQty", rs.getInt("allocated_qty"));      // số túi sẽ hoàn kho nếu hủy
                return m;
            }
        }
    }

    /* =========================================================
       [147] IN LẠI HÓA ĐƠN: ghi nhật ký, trả về số lần đã in lại (kể cả lần này)
       ========================================================= */
    public int logReprint(long invoiceId, int userId) throws SQLException {
        try (Connection con = connect()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT status FROM sales_invoices WHERE sales_invoice_id = ?")) {
                ps.setLong(1, invoiceId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy đơn");
                    if ("PENDING".equals(rs.getString(1))) {
                        throw new IllegalArgumentException("Đơn chưa xác nhận, chưa có hóa đơn để in lại");
                    }
                }
            }
            writeAudit(con, userId, "REPRINT_INVOICE", invoiceId, null);
            try (PreparedStatement ps = con.prepareStatement("""
                    SELECT COUNT(*) FROM audit_logs
                    WHERE entity_type = 'sales_invoices' AND entity_id = ? AND action = 'REPRINT_INVOICE'
                    """)) {
                ps.setLong(1, invoiceId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        }
    }

    /* =========================================================
       [154] HỦY ĐƠN ĐÃ XÁC NHẬN (chỉ chủ cửa hàng, controller kiểm quyền)
       restock = true  -> [156/158] hoàn kho về đúng lô, đúng số đã trừ
       refund  = true  -> [157] ghi khoản REFUND tiền mặt = số đã thu thực tế
       Tất cả trong 1 transaction.
       ========================================================= */
    public void cancelPaidOrder(long invoiceId, int userId, String reason, boolean restock, boolean refund)
            throws SQLException {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Nhập lý do hủy");
        reason = reason.trim();

        try (Connection con = connect()) {
            con.setAutoCommit(false);
            try {
                // 1. Khóa đơn, chỉ hủy được đơn đã xác nhận
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT status FROM sales_invoices WHERE sales_invoice_id = ? FOR UPDATE")) {
                    ps.setLong(1, invoiceId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy đơn");
                        if (!"PAID".equals(rs.getString(1))) {
                            throw new IllegalArgumentException("Chỉ hủy được đơn đã xác nhận");
                        }
                    }
                }

                // 2. [154.1] Hủy đơn
                try (PreparedStatement ps = con.prepareStatement("""
                        UPDATE sales_invoices
                        SET status = 'CANCELLED', cancelled_by = ?, cancelled_at = now(),
                            cancel_reason = ?, is_restocked = ?
                        WHERE sales_invoice_id = ? AND status = 'PAID'
                        """)) {
                    ps.setInt(1, userId);
                    ps.setString(2, reason);
                    ps.setBoolean(3, restock);
                    ps.setLong(4, invoiceId);
                    ps.executeUpdate();
                }

                // 3. [158.1] Hoàn kho đúng lô, đúng số lượng đã trừ
                int restockQty = 0;
                if (restock) {
                    try (PreparedStatement ps = con.prepareStatement("""
                            INSERT INTO stock_movements (lot_id, movement_type, quantity_change, sales_invoice_id, performed_by)
                            SELECT a.lot_id, 'SALE_RETURN', a.quantity, ?, ?
                            FROM sales_line_allocations a
                            JOIN sales_invoice_lines l ON l.sales_line_id = a.sales_line_id
                            WHERE l.sales_invoice_id = ?
                            """)) {
                        ps.setLong(1, invoiceId);
                        ps.setInt(2, userId);
                        ps.setLong(3, invoiceId);
                        restockQty = ps.executeUpdate();
                    }
                }

                // 4. [157] Trừ tiền: 1 khoản REFUND tiền mặt bằng số đã thu thực tế (thu - hoàn)
                BigDecimal refundAmount = BigDecimal.ZERO;
                if (refund) {
                    refundAmount = paidAmount(con, invoiceId);
                    if (refundAmount.signum() > 0) {
                        try (PreparedStatement ps = con.prepareStatement("""
                                INSERT INTO payments (sales_invoice_id, payment_method_id, amount, status,
                                                      payment_kind, confirmed_by, paid_at)
                                VALUES (?, (SELECT payment_method_id FROM payment_methods WHERE method_code = 'CASH'),
                                        ?, 'SUCCESS', 'REFUND', ?, now())
                                """)) {
                            ps.setLong(1, invoiceId);
                            ps.setBigDecimal(2, refundAmount);
                            ps.setInt(3, userId);
                            ps.executeUpdate();
                        }
                    }
                }

                // 5. [154.2] Nhật ký thao tác nhạy cảm
                try (PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO audit_logs (user_id, action, entity_type, entity_id, detail)
                        VALUES (?, 'CANCEL_PAID_INVOICE', 'sales_invoices', ?,
                                jsonb_build_object('reason', CAST(? AS TEXT), 'restocked', ?,
                                                   'restockRows', ?, 'refundAmount', ?))
                        """)) {
                    ps.setInt(1, userId);
                    ps.setLong(2, invoiceId);
                    ps.setString(3, reason);
                    ps.setBoolean(4, restock);
                    ps.setInt(5, restockQty);
                    ps.setBigDecimal(6, refundAmount);
                    ps.executeUpdate();
                }

                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /* ---------- Hàm phụ ---------- */

    /** Đã thu thực tế = thu - hoàn (giao dịch SUCCESS) */
    private BigDecimal paidAmount(Connection con, long invoiceId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT COALESCE(SUM(CASE WHEN payment_kind = 'CHARGE' THEN amount ELSE -amount END), 0)
                FROM payments WHERE sales_invoice_id = ? AND status = 'SUCCESS'
                """)) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private void writeAudit(Connection con, int userId, String action, long invoiceId, String reason)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                INSERT INTO audit_logs (user_id, action, entity_type, entity_id, detail)
                VALUES (?, ?, 'sales_invoices', ?,
                        CASE WHEN CAST(? AS TEXT) IS NULL THEN NULL ELSE jsonb_build_object('reason', CAST(? AS TEXT)) END)
                """)) {
            ps.setInt(1, userId);
            ps.setString(2, action);
            ps.setLong(3, invoiceId);
            ps.setString(4, reason);
            ps.setString(5, reason);
            ps.executeUpdate();
        }
    }

    private Connection connect() throws SQLException {
        Connection con = DBConnect.getConnection();
        if (con == null) throw new SQLException("Không kết nối được database (xem log Tomcat)");
        return con;
    }
}