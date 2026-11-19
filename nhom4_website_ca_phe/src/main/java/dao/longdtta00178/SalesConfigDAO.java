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
 * Trang cấu hình bán hàng (longdtta001784), chỉ chủ cửa hàng (quyền SALES_CONFIG).
 * 159 lưu / 164 xem thông tin hóa đơn · 163 bật tắt phương thức thanh toán · 162 thanh toán ngoại lệ.
 */
public class SalesConfigDAO {

    /** Các khóa được sửa trên trang (system_settings, tiền tố invoice.) -> nhãn hiển thị */
    public static final Map<String, String> INVOICE_KEYS = new LinkedHashMap<>();
    static {
        INVOICE_KEYS.put("store_name", "Tên cửa hàng");
        INVOICE_KEYS.put("address", "Địa chỉ");
        INVOICE_KEYS.put("phone", "Số điện thoại");
        INVOICE_KEYS.put("tax_code", "Mã số thuế");
        INVOICE_KEYS.put("vat_rate", "Thuế suất GTGT (%)");
        INVOICE_KEYS.put("footer_note", "Dòng cuối hóa đơn");
    }

    /* =========================================================
       [164] XEM CẤU HÌNH THÔNG TIN HÓA ĐƠN
       Trả về đủ các khóa INVOICE_KEYS (khóa chưa có trong DB thì value rỗng)
       ========================================================= */
    public List<Map<String, Object>> findInvoiceSettings() throws SQLException {
        Map<String, Map<String, Object>> found = new LinkedHashMap<>();
        String sql = """
                SELECT s.setting_key, s.setting_value, u.full_name AS updated_by_name, s.updated_at
                FROM system_settings s
                LEFT JOIN users u ON u.user_id = s.updated_by
                WHERE s.setting_key LIKE 'invoice.%'
                """;
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("value", rs.getString("setting_value"));
                m.put("updatedByName", rs.getString("updated_by_name"));
                m.put("updatedAt", rs.getObject("updated_at", OffsetDateTime.class));
                found.put(rs.getString("setting_key").substring("invoice.".length()), m);
            }
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, String> e : INVOICE_KEYS.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            Map<String, Object> db = found.getOrDefault(e.getKey(), Map.of());
            row.put("key", e.getKey());
            row.put("label", e.getValue());
            row.put("value", db.getOrDefault("value", ""));
            row.put("updatedByName", db.get("updatedByName"));
            row.put("updatedAt", db.get("updatedAt"));
            list.add(row);
        }
        return list;
    }

    /* =========================================================
       [159] LƯU CẤU HÌNH THÔNG TIN HÓA ĐƠN (chỉ khóa đổi giá trị mới ghi), 1 transaction
       values: store_name -> "Nhóm 4 Coffee" ...
       Trả về số khóa đã đổi
       ========================================================= */
    public int saveInvoiceSettings(Map<String, String> values, int userId) throws SQLException {
        // Kiểm tra dữ liệu
        String storeName = values.get("store_name");
        if (storeName == null || storeName.isBlank()) throw new IllegalArgumentException("Nhập tên cửa hàng");
        String vat = values.get("vat_rate");
        try {
            BigDecimal rate = new BigDecimal(vat == null || vat.isBlank() ? "0" : vat.trim());
            if (rate.signum() < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) throw new NumberFormatException();
            values.put("vat_rate", rate.stripTrailingZeros().toPlainString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Thuế suất GTGT phải là số từ 0 đến 100");
        }
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (!INVOICE_KEYS.containsKey(e.getKey())) throw new IllegalArgumentException("Khóa không hợp lệ: " + e.getKey());
            if (e.getValue() != null && e.getValue().length() > 200) {
                throw new IllegalArgumentException(INVOICE_KEYS.get(e.getKey()) + " tối đa 200 ký tự");
            }
        }

        String sql = """
                INSERT INTO system_settings (setting_key, setting_value, description, updated_by, updated_at)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (setting_key) DO UPDATE
                SET setting_value = EXCLUDED.setting_value, updated_by = EXCLUDED.updated_by, updated_at = now()
                WHERE system_settings.setting_value IS DISTINCT FROM EXCLUDED.setting_value
                """;
        int changed = 0;
        try (Connection con = connect()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                for (Map.Entry<String, String> e : values.entrySet()) {
                    ps.setString(1, "invoice." + e.getKey());
                    ps.setString(2, e.getValue() == null ? "" : e.getValue().trim());
                    ps.setString(3, INVOICE_KEYS.get(e.getKey()));
                    ps.setInt(4, userId);
                    changed += ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
        return changed;
    }

    /* =========================================================
       [163] PHƯƠNG THỨC THANH TOÁN: danh sách + bật / tắt
       Tiền mặt luôn bật (cần để thu tiền và hoàn tiền cho khách).
       ========================================================= */
    public List<Map<String, Object>> findPaymentMethods() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement("""
                     SELECT payment_method_id, method_code, method_name, is_online, is_active
                     FROM payment_methods ORDER BY payment_method_id
                     """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getInt("payment_method_id"));
                m.put("code", rs.getString("method_code"));
                m.put("name", rs.getString("method_name"));
                m.put("online", rs.getBoolean("is_online"));
                m.put("active", rs.getBoolean("is_active"));
                list.add(m);
            }
        }
        return list;
    }

    public void setPaymentMethodActive(int methodId, boolean active, int userId) throws SQLException {
        try (Connection con = connect()) {
            con.setAutoCommit(false);
            try {
                String code;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT method_code FROM payment_methods WHERE payment_method_id = ? FOR UPDATE")) {
                    ps.setInt(1, methodId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy phương thức");
                        code = rs.getString(1);
                    }
                }
                if ("CASH".equals(code) && !active) {
                    throw new IllegalArgumentException("Không tắt được Tiền mặt (dùng để thu tiền và hoàn tiền)");
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE payment_methods SET is_active = ? WHERE payment_method_id = ?")) {
                    ps.setBoolean(1, active);
                    ps.setInt(2, methodId);
                    ps.executeUpdate();
                }
                audit(con, userId, active ? "ENABLE_PAYMENT_METHOD" : "DISABLE_PAYMENT_METHOD",
                        "payment_methods", methodId, code);
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /* =========================================================
       [162] THANH TOÁN NGOẠI LỆ
       kind:
         PAID_NOT_CONFIRMED  đã nhận tiền nhưng đơn còn chờ (trả thiếu, hết tồn lúc tự xác nhận)
                             -> mở đơn ở màn bán hàng để thu thêm / chỉnh rồi xác nhận
         EXPIRED_QR          QR quá 15 phút còn chờ -> đóng giao dịch (FAILED)
         PAID_BUT_CLOSED     VNPay báo trả thành công nhưng giao dịch đã bị hủy (khách quét QR cũ)
                             -> đơn còn chờ: ghi nhận đã thu; đơn đã đóng: hoàn tiền cho khách
       ========================================================= */
    public List<Map<String, Object>> findPaymentExceptions() throws SQLException {
        String sql = """
                SELECT p.payment_id, si.sales_invoice_id, si.invoice_no, si.status AS invoice_status,
                       pm.method_name, p.amount, p.status AS payment_status, g.txn_ref, g.response_code,
                       p.created_at, p.paid_at,
                       CASE WHEN g.response_code = '00' AND p.status <> 'SUCCESS' THEN 'PAID_BUT_CLOSED'
                            WHEN p.status = 'SUCCESS' THEN 'PAID_NOT_CONFIRMED'
                            ELSE 'EXPIRED_QR' END AS kind
                FROM payments p
                JOIN sales_invoices si  ON si.sales_invoice_id = p.sales_invoice_id
                JOIN payment_methods pm ON pm.payment_method_id = p.payment_method_id
                LEFT JOIN payment_gateway_transactions g ON g.payment_id = p.payment_id
                WHERE p.payment_kind = 'CHARGE' AND (
                      (p.status = 'SUCCESS' AND si.status = 'PENDING')
                   OR (p.status = 'PENDING' AND p.created_at < now() - INTERVAL '15 minutes')
                   OR (g.response_code = '00' AND p.status <> 'SUCCESS'))
                ORDER BY p.created_at DESC
                """;
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("paymentId", rs.getLong("payment_id"));
                m.put("salesInvoiceId", rs.getLong("sales_invoice_id"));
                m.put("invoiceNo", rs.getString("invoice_no"));
                m.put("invoiceStatus", rs.getString("invoice_status"));
                m.put("methodName", rs.getString("method_name"));
                m.put("amount", rs.getBigDecimal("amount"));
                m.put("paymentStatus", rs.getString("payment_status"));
                m.put("txnRef", rs.getString("txn_ref"));
                m.put("responseCode", rs.getString("response_code"));
                m.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
                m.put("paidAt", rs.getObject("paid_at", OffsetDateTime.class));
                m.put("kind", rs.getString("kind"));
                list.add(m);
            }
        }
        return list;
    }

    /** [162.2] Đóng giao dịch QR quá hạn / lỗi */
    public void closePayment(long paymentId, int userId) throws SQLException {
        try (Connection con = connect()) {
            con.setAutoCommit(false);
            try {
                int n;
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE payments SET status = 'FAILED' WHERE payment_id = ? AND status = 'PENDING'")) {
                    ps.setLong(1, paymentId);
                    n = ps.executeUpdate();
                }
                if (n == 0) throw new IllegalArgumentException("Giao dịch không còn ở trạng thái chờ");
                audit(con, userId, "CLOSE_PAYMENT", "payments", paymentId, null);
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * [162.3] Ghi nhận tiền VNPay đã về cho giao dịch đã bị hủy (khách quét QR cũ).
     * Chỉ khi cổng báo thành công (response 00) và đơn còn chờ. Sau đó mở đơn ở màn bán hàng để xác nhận.
     */
    public void acceptPaidPayment(long paymentId, int userId) throws SQLException {
        try (Connection con = connect()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement("""
                        SELECT p.status, g.response_code, si.status AS invoice_status
                        FROM payments p
                        JOIN sales_invoices si ON si.sales_invoice_id = p.sales_invoice_id
                        LEFT JOIN payment_gateway_transactions g ON g.payment_id = p.payment_id
                        WHERE p.payment_id = ?
                        FOR UPDATE OF p
                        """)) {
                    ps.setLong(1, paymentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy giao dịch");
                        if (!"00".equals(rs.getString("response_code")) || "SUCCESS".equals(rs.getString("status"))) {
                            throw new IllegalArgumentException("Giao dịch này không cần ghi nhận");
                        }
                        if (!"PENDING".equals(rs.getString("invoice_status"))) {
                            throw new IllegalArgumentException("Đơn đã đóng, không ghi thêm tiền. Hoàn tiền cho khách ngoài hệ thống.");
                        }
                    }
                }
                try (PreparedStatement ps = con.prepareStatement("""
                        UPDATE payments p SET status = 'SUCCESS',
                               paid_at = COALESCE((SELECT g.pay_date FROM payment_gateway_transactions g
                                                   WHERE g.payment_id = p.payment_id), now())
                        WHERE p.payment_id = ?
                        """)) {
                    ps.setLong(1, paymentId);
                    ps.executeUpdate();
                }
                audit(con, userId, "ACCEPT_LATE_PAYMENT", "payments", paymentId, null);
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /* ---------- Hàm phụ ---------- */

    private void audit(Connection con, int userId, String action, String entityType, long entityId, String code)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                INSERT INTO audit_logs (user_id, action, entity_type, entity_id, detail)
                VALUES (?, ?, ?, ?, CASE WHEN CAST(? AS TEXT) IS NULL THEN NULL
                                         ELSE jsonb_build_object('code', CAST(? AS TEXT)) END)
                """)) {
            ps.setInt(1, userId);
            ps.setString(2, action);
            ps.setString(3, entityType);
            ps.setLong(4, entityId);
            ps.setString(5, code);
            ps.setString(6, code);
            ps.executeUpdate();
        }
    }

    private Connection connect() throws SQLException {
        Connection con = DBConnect.getConnection();
        if (con == null) throw new SQLException("Không kết nối được database (xem log Tomcat)");
        return con;
    }
}