package dao.longdtta00178;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import uri.DBConnect;

/**
 * Thanh toán VNPay ở màn bán hàng.
 * 133 tạo giao dịch chờ (QR), 160 theo dõi trạng thái, 161 xử lý kết quả cổng (Return / IPN),
 * 139 ghi nhận chuyển khoản -> đủ tiền thì tự xác nhận đơn + trừ kho (140, 142).
 */
public class VnpayDAO {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private final SalesOrderDAO orderDao = new SalesOrderDAO();

    /* =========================================================
       [133] TẠO GIAO DỊCH CHỜ THANH TOÁN
       Số tiền = còn phải thu. QR cũ còn chờ của đơn bị hủy (133.2).
       Trả về: paymentId, txnRef, amount, invoiceNo
       ========================================================= */
    public Map<String, Object> createPayment(long invoiceId) throws SQLException {
        try (Connection con = connect()) {
            con.setAutoCommit(false);
            try {
                String invoiceNo;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT invoice_no, status FROM sales_invoices WHERE sales_invoice_id = ? FOR UPDATE")) {
                    ps.setLong(1, invoiceId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy đơn");
                        if (!"PENDING".equals(rs.getString("status"))) {
                            throw new IllegalArgumentException("Đơn đã xác nhận hoặc đã hủy");
                        }
                        invoiceNo = rs.getString("invoice_no");
                    }
                }

                BigDecimal due = dueAmount(con, invoiceId);
                if (due.signum() <= 0) throw new IllegalArgumentException("Đơn trống hoặc đã thu đủ tiền");

                // 133.2 hủy QR cũ còn chờ
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE payments SET status = 'CANCELLED' WHERE sales_invoice_id = ? AND status = 'PENDING'")) {
                    ps.setLong(1, invoiceId);
                    ps.executeUpdate();
                }

                // 133.3 tạo giao dịch chờ
                long paymentId;
                try (PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO payments (sales_invoice_id, payment_method_id, amount, status, payment_kind)
                        VALUES (?, (SELECT payment_method_id FROM payment_methods
                                    WHERE method_code = 'VNPAY' AND is_active), ?, 'PENDING', 'CHARGE')
                        RETURNING payment_id
                        """)) {
                    ps.setLong(1, invoiceId);
                    ps.setBigDecimal(2, due);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        paymentId = rs.getLong(1);
                    }
                } catch (SQLException e) {
                    if ("23502".equals(e.getSQLState())) {   // payment_method_id NULL = VNPay đang tắt
                        throw new IllegalArgumentException("Phương thức VNPay đang tắt");
                    }
                    throw e;
                }

                // 133.4 mã giao dịch gửi sang VNPay: chỉ chữ + số, không trùng. VD HD2611190002P15
                String txnRef = invoiceNo.replace("-", "") + "P" + paymentId;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO payment_gateway_transactions (payment_id, txn_ref) VALUES (?, ?)")) {
                    ps.setLong(1, paymentId);
                    ps.setString(2, txnRef);
                    ps.executeUpdate();
                }

                con.commit();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("paymentId", paymentId);
                result.put("txnRef", txnRef);
                result.put("amount", due);
                result.put("invoiceNo", invoiceNo);
                return result;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /* =========================================================
       [160] TRẠNG THÁI THANH TOÁN (màn QR hỏi lại mỗi 3 giây)
       ========================================================= */
    public Map<String, Object> findPaymentStatus(long paymentId) throws SQLException {
        String sql = """
                SELECT p.payment_id, p.sales_invoice_id, p.status AS payment_status, p.amount,
                       g.response_code, si.status AS invoice_status
                FROM payments p
                JOIN sales_invoices si ON si.sales_invoice_id = p.sales_invoice_id
                LEFT JOIN payment_gateway_transactions g ON g.payment_id = p.payment_id
                WHERE p.payment_id = ?
                """;
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy giao dịch");
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("paymentId", rs.getLong("payment_id"));
                result.put("salesInvoiceId", rs.getLong("sales_invoice_id"));
                result.put("paymentStatus", rs.getString("payment_status"));   // PENDING | SUCCESS | FAILED | CANCELLED
                result.put("invoiceStatus", rs.getString("invoice_status"));   // PENDING | PAID | CANCELLED
                result.put("responseCode", rs.getString("response_code"));
                result.put("amount", rs.getBigDecimal("amount"));
                return result;
            }
        }
    }

    /* =========================================================
       [161] XỬ LÝ KẾT QUẢ VNPAY (Return URL hoặc IPN), gọi nhiều lần vẫn an toàn
       Trả về mã theo chuẩn IPN của VNPay:
         00 xử lý xong | 01 không có giao dịch | 02 đã xử lý trước đó | 04 sai số tiền | 97 sai chữ ký
       ========================================================= */
    public String handleResult(Map<String, String> params, String source, boolean signatureValid, String payloadJson)
            throws SQLException {
        try (Connection con = connect()) {
            con.setAutoCommit(false);
            try {
                // 161.2 tìm giao dịch theo vnp_TxnRef, khóa dòng payments
                Long paymentId = null;
                long invoiceId = 0;
                BigDecimal amount = null;
                String status = null;
                try (PreparedStatement ps = con.prepareStatement("""
                        SELECT p.payment_id, p.sales_invoice_id, p.amount, p.status
                        FROM payment_gateway_transactions g
                        JOIN payments p ON p.payment_id = g.payment_id
                        WHERE g.txn_ref = ?
                        FOR UPDATE OF p
                        """)) {
                    ps.setString(1, params.getOrDefault("vnp_TxnRef", ""));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            paymentId = rs.getLong("payment_id");
                            invoiceId = rs.getLong("sales_invoice_id");
                            amount = rs.getBigDecimal("amount");
                            status = rs.getString("status");
                        }
                    }
                }

                // 161.1 luôn lưu log thô để đối soát (kể cả sai chữ ký)
                try (PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO payment_gateway_logs (payment_id, source, payload, signature_valid)
                        VALUES (?, ?, CAST(? AS JSONB), ?)
                        """)) {
                    ps.setObject(1, paymentId, java.sql.Types.BIGINT);
                    ps.setString(2, source);
                    ps.setString(3, payloadJson);
                    ps.setBoolean(4, signatureValid);
                    ps.executeUpdate();
                }

                String code;
                if (!signatureValid) {
                    code = "97";
                } else if (paymentId == null) {
                    code = "01";
                } else if (!amountMatches(params.get("vnp_Amount"), amount)) {
                    code = "04";
                                } else if (!"PENDING".equals(status)) {
                    code = "02";
                    // [162] Khách trả bằng QR đã bị hủy / đóng: lưu kết quả cổng để chủ xử lý ở trang cấu hình
                    if (!"SUCCESS".equals(status) && "00".equals(params.get("vnp_ResponseCode"))
                            && "00".equals(params.get("vnp_TransactionStatus"))) {
                        try (PreparedStatement ps = con.prepareStatement("""
                                UPDATE payment_gateway_transactions
                                SET gateway_txn_no = ?, bank_code = ?, response_code = ?, pay_date = ?
                                WHERE payment_id = ? AND response_code IS NULL
                                """)) {
                            ps.setString(1, params.get("vnp_TransactionNo"));
                            ps.setString(2, params.get("vnp_BankCode"));
                            ps.setString(3, params.get("vnp_ResponseCode"));
                            ps.setObject(4, parsePayDate(params.get("vnp_PayDate")));
                            ps.setLong(5, paymentId);
                            ps.executeUpdate();
                        }
                    }
                } else {
                    code = "00";
                    boolean success = "00".equals(params.get("vnp_ResponseCode"))
                            && "00".equals(params.get("vnp_TransactionStatus"));

                    // 161.3 lưu kết quả cổng trả về
                    try (PreparedStatement ps = con.prepareStatement("""
                            UPDATE payment_gateway_transactions
                            SET gateway_txn_no = ?, bank_code = ?, response_code = ?, pay_date = ?
                            WHERE payment_id = ?
                            """)) {
                        ps.setString(1, params.get("vnp_TransactionNo"));
                        ps.setString(2, params.get("vnp_BankCode"));
                        ps.setString(3, params.get("vnp_ResponseCode"));
                        ps.setObject(4, parsePayDate(params.get("vnp_PayDate")));
                        ps.setLong(5, paymentId);
                        ps.executeUpdate();
                    }

                    // 161.4 cập nhật giao dịch
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE payments SET status = ?, paid_at = CASE WHEN ? THEN now() END WHERE payment_id = ?")) {
                        ps.setString(1, success ? "SUCCESS" : "FAILED");
                        ps.setBoolean(2, success);
                        ps.setLong(3, paymentId);
                        ps.executeUpdate();
                    }

                    // 139 -> 140 + 142: đã thu đủ thì tự xác nhận đơn và trừ kho.
                    // Không xác nhận được (VD hết tồn) thì vẫn giữ tiền đã thu, đơn còn PENDING để nhân viên xử lý.
                    if (success) {
                        Savepoint beforeConfirm = con.setSavepoint();
                        try {
                            orderDao.confirmInTransaction(con, invoiceId, null, null);
                        } catch (SQLException | RuntimeException e) {
                            con.rollback(beforeConfirm);
                            System.err.println("VNPay: đã thu tiền nhưng chưa xác nhận được đơn "
                                    + invoiceId + ": " + e.getMessage());
                        }
                    }
                }

                con.commit();
                return code;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** vnp_Amount = số tiền x 100 */
    private static boolean amountMatches(String vnpAmount, BigDecimal amount) {
        try {
            return amount != null && new BigDecimal(vnpAmount).compareTo(amount.multiply(BigDecimal.valueOf(100))) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** "20261119153045" (giờ VN) -> OffsetDateTime */
    private static OffsetDateTime parsePayDate(String value) {
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    .atZone(VIETNAM).toOffsetDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal dueAmount(Connection con, long invoiceId) throws SQLException {
        String sql = """
                SELECT (SELECT COALESCE(SUM(quantity * unit_price), 0)
                        FROM sales_invoice_lines WHERE sales_invoice_id = ?)
                     - (SELECT COALESCE(SUM(CASE WHEN payment_kind = 'CHARGE' THEN amount ELSE -amount END), 0)
                        FROM payments WHERE sales_invoice_id = ? AND status = 'SUCCESS')
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, invoiceId);
            ps.setLong(2, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private Connection connect() throws SQLException {
        Connection con = DBConnect.getConnection();
        if (con == null) throw new SQLException("Không kết nối được database (xem log Tomcat)");
        return con;
    }
}