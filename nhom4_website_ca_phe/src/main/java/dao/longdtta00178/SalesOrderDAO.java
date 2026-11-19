package dao.longdtta00178;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import models.enums.PaymentKind;
import models.enums.PaymentStatus;
import models.longdtta00178.Payment;
import models.longdtta00178.SalesLineAllocation;
import models.enums.SalesInvoiceStatus;
import models.longdtta00178.SalesInvoice;
import models.longdtta00178.SalesInvoiceLine;
import uri.DBConnect;
import java.sql.Types;
/**
 * Đơn đang lập ở màn bán hàng.
 * UC 130 tạo đơn, 132 thêm gói / đổi số lượng, 134 xóa gói, 136 giới hạn theo tồn còn hạn.
 * Lỗi nghiệp vụ (đơn đã đóng, vượt tồn, chưa có giá...) ném IllegalArgumentException
 * -> controller trả mã 400 kèm câu báo lỗi cho nhân viên.
 */
public class SalesOrderDAO {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
        private final SalesLotDAO lotDao = new SalesLotDAO();

    /** Tồn bán được của 1 gói = tổng tồn các lô CÒN HẠN (câu 136) */
    private static final String SQL_SELLABLE_QTY = """
            SELECT COALESCE(SUM(m.quantity_change), 0) AS qty
            FROM lots lo
            JOIN purchase_invoice_lines pil ON pil.purchase_line_id = lo.purchase_line_id
            JOIN stock_movements m          ON m.lot_id = lo.lot_id
            WHERE pil.package_id = ?
              AND lo.expiry_date >= (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
            """;

    /* =========================================================
       [130] TẠO ĐƠN
       ========================================================= */

    /** Tạo đơn PENDING, mã dạng HDyyMMdd-0001. Trùng mã (2 quầy tạo cùng lúc) thì thử số tiếp theo. */
    public long createInvoice(int userId) throws SQLException {
        String prefix = "HD" + LocalDate.now(VIETNAM).format(DateTimeFormatter.ofPattern("yyMMdd")) + "-";

        try (Connection con = connect()) {
            int seq = countTodayInvoices(con) + 1;
            for (int attempt = 0; attempt < 5; attempt++, seq++) {
                String invoiceNo = prefix + String.format("%04d", seq);
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO sales_invoices (invoice_no, created_by) VALUES (?, ?) RETURNING sales_invoice_id")) {
                    ps.setString(1, invoiceNo);
                    ps.setInt(2, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        return rs.getLong(1);
                    }
                } catch (SQLException e) {
                    if (!"23505".equals(e.getSQLState())) throw e;   // 23505 = trùng UNIQUE invoice_no
                }
            }
        }
        throw new SQLException("Không sinh được mã hóa đơn, hãy thử lại");
    }

    private int countTodayInvoices(Connection con) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM sales_invoices
                WHERE created_at >= ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date)::timestamp
                                    AT TIME ZONE 'Asia/Ho_Chi_Minh'
                """;
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /* =========================================================
       ĐỌC ĐƠN: đầu đơn (C4 + tổng tiền C3 + đã thu) và dòng hàng (C5 + tồn tối đa)
       ========================================================= */

    /** null nếu không có đơn */
    public SalesInvoice findInvoice(long invoiceId) throws SQLException {
        String sql = """
                SELECT si.sales_invoice_id, si.invoice_no, si.status, si.created_at, si.created_by,
                       u.full_name AS cashier_name,
                       (SELECT COALESCE(SUM(l.quantity * l.unit_price), 0)
                        FROM sales_invoice_lines l
                        WHERE l.sales_invoice_id = si.sales_invoice_id) AS total_amount,
                                              (SELECT COALESCE(SUM(CASE WHEN p.payment_kind = 'CHARGE' THEN p.amount ELSE -p.amount END), 0)
                        FROM payments p
                        WHERE p.sales_invoice_id = si.sales_invoice_id AND p.status = 'SUCCESS') AS paid_amount
                FROM sales_invoices si
                JOIN users u ON u.user_id = si.created_by
                WHERE si.sales_invoice_id = ?
                """;
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                SalesInvoice inv = new SalesInvoice();
                inv.setSalesInvoiceId(rs.getLong("sales_invoice_id"));
                inv.setInvoiceNo(rs.getString("invoice_no"));
                inv.setStatus(SalesInvoiceStatus.valueOf(rs.getString("status")));
                inv.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
                inv.setCreatedBy(rs.getInt("created_by"));
                inv.setCashierName(rs.getString("cashier_name"));
                inv.setTotalAmount(rs.getBigDecimal("total_amount"));
                inv.setPaidAmount(rs.getBigDecimal("paid_amount"));
                return inv;
            }
        }
    }

    public List<SalesInvoiceLine> findLines(long invoiceId) throws SQLException {
        String sql = """
                SELECT l.sales_line_id, l.sales_invoice_id, l.package_id, l.quantity, l.unit_price,
                       pk.sku, pk.weight_g, v.variant_name, g.is_whole_bean,
                       (SELECT COALESCE(SUM(m.quantity_change), 0)
                        FROM lots lo
                        JOIN purchase_invoice_lines pil ON pil.purchase_line_id = lo.purchase_line_id
                        JOIN stock_movements m          ON m.lot_id = lo.lot_id
                        WHERE pil.package_id = l.package_id
                          AND lo.expiry_date >= (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date) AS max_qty
                FROM sales_invoice_lines l
                JOIN packages pk        ON pk.package_id = l.package_id
                JOIN product_variants v ON v.variant_id = pk.variant_id
                JOIN grind_sizes g      ON g.grind_size_id = pk.grind_size_id
                WHERE l.sales_invoice_id = ?
                ORDER BY l.sales_line_id
                """;
        List<SalesInvoiceLine> lines = new ArrayList<>();
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SalesInvoiceLine line = new SalesInvoiceLine();
                    line.setSalesLineId(rs.getLong("sales_line_id"));
                    line.setSalesInvoiceId(rs.getLong("sales_invoice_id"));
                    line.setPackageId(rs.getInt("package_id"));
                    line.setQuantity(rs.getInt("quantity"));
                    line.setUnitPrice(rs.getBigDecimal("unit_price"));
                    line.setSku(rs.getString("sku"));
                    line.setWeightG(rs.getInt("weight_g"));
                    line.setVariantName(rs.getString("variant_name"));
                    line.setWholeBean(rs.getBoolean("is_whole_bean"));
                    line.setMaxQty(rs.getInt("max_qty"));
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    /* =========================================================
       [132] THÊM GÓI VÀO ĐƠN (gói đã có thì cộng dồn số lượng)
       ========================================================= */
    public void addPackage(long invoiceId, int packageId, int quantity) throws SQLException {
        if (quantity < 1) throw new IllegalArgumentException("Số lượng phải từ 1 trở lên");

        try (Connection con = connect()) {
            requirePending(con, invoiceId);

            // Gói còn bán + giá hiện hành (C1)
            String sqlPrice = """
                    SELECT pk.status AS pkg_status, v.status AS variant_status, cp.sale_price
                    FROM packages pk
                    JOIN product_variants v ON v.variant_id = pk.variant_id
                    LEFT JOIN v_package_current_price cp ON cp.package_id = pk.package_id
                    WHERE pk.package_id = ?
                    """;
            BigDecimal price;
            try (PreparedStatement ps = con.prepareStatement(sqlPrice)) {
                ps.setInt(1, packageId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy gói bán");
                    if (!"ACTIVE".equals(rs.getString("pkg_status")) || !"ACTIVE".equals(rs.getString("variant_status"))) {
                        throw new IllegalArgumentException("Gói này đã ngừng bán");
                    }
                    price = rs.getBigDecimal("sale_price");
                    if (price == null) throw new IllegalArgumentException("Gói này chưa có giá bán");
                }
            }

            // Số đã có trong đơn + số thêm không được vượt tồn còn hạn (136)
            int inOrder = 0;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT quantity FROM sales_invoice_lines WHERE sales_invoice_id = ? AND package_id = ?")) {
                ps.setLong(1, invoiceId);
                ps.setInt(2, packageId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) inOrder = rs.getInt(1);
                }
            }
            int sellable = sellableQty(con, packageId);
            if (inOrder + quantity > sellable) {
                throw new IllegalArgumentException(sellable == 0
                        ? "Gói này đã hết hàng"
                        : "Chỉ còn " + sellable + " túi, trong đơn đã có " + inOrder);
            }

            // 132.1 thêm dòng, trùng gói thì cộng dồn
            String sqlUpsert = """
                    INSERT INTO sales_invoice_lines (sales_invoice_id, package_id, quantity, unit_price)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (sales_invoice_id, package_id)
                    DO UPDATE SET quantity = sales_invoice_lines.quantity + EXCLUDED.quantity
                    """;
            try (PreparedStatement ps = con.prepareStatement(sqlUpsert)) {
                ps.setLong(1, invoiceId);
                ps.setInt(2, packageId);
                ps.setInt(3, quantity);
                ps.setBigDecimal(4, price);
                ps.executeUpdate();
            }
        }
    }

    /* =========================================================
       [132.2] ĐỔI SỐ LƯỢNG 1 DÒNG
       ========================================================= */
    public void updateQuantity(long invoiceId, long lineId, int quantity) throws SQLException {
        if (quantity < 1) throw new IllegalArgumentException("Số lượng phải từ 1 trở lên, muốn bỏ thì bấm ✕");

        try (Connection con = connect()) {
            requirePending(con, invoiceId);

            int packageId;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT package_id FROM sales_invoice_lines WHERE sales_line_id = ? AND sales_invoice_id = ?")) {
                ps.setLong(1, lineId);
                ps.setLong(2, invoiceId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy dòng hàng trong đơn");
                    packageId = rs.getInt(1);
                }
            }

            int sellable = sellableQty(con, packageId);
            if (quantity > sellable) throw new IllegalArgumentException("Chỉ còn " + sellable + " túi");

            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE sales_invoice_lines SET quantity = ? WHERE sales_line_id = ?")) {
                ps.setInt(1, quantity);
                ps.setLong(2, lineId);
                ps.executeUpdate();
            }
        }
    }

    /* =========================================================
       [134] XÓA GÓI KHỎI ĐƠN
       ========================================================= */
    public void removeLine(long invoiceId, long lineId) throws SQLException {
        try (Connection con = connect()) {
            requirePending(con, invoiceId);
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM sales_invoice_lines WHERE sales_line_id = ? AND sales_invoice_id = ?")) {
                ps.setLong(1, lineId);
                ps.setLong(2, invoiceId);
                if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Không tìm thấy dòng hàng trong đơn");
            }
        }
    }

        /* =========================================================
       [C9] CÁC KHOẢN THU / HOÀN CỦA ĐƠN
       ========================================================= */
    public List<Payment> findPayments(long invoiceId) throws SQLException {
        String sql = """
                SELECT p.payment_id, p.amount, p.status, p.payment_kind, p.created_at, p.paid_at,
                       pm.method_code, pm.method_name
                FROM payments p
                JOIN payment_methods pm ON pm.payment_method_id = p.payment_method_id
                WHERE p.sales_invoice_id = ?
                ORDER BY p.created_at, p.payment_id
                """;
        List<Payment> list = new ArrayList<>();
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Payment p = new Payment();
                    p.setPaymentId(rs.getLong("payment_id"));
                    p.setSalesInvoiceId(invoiceId);
                    p.setAmount(rs.getBigDecimal("amount"));
                    p.setStatus(PaymentStatus.valueOf(rs.getString("status")));
                    p.setPaymentKind(PaymentKind.valueOf(rs.getString("payment_kind")));
                    p.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
                    p.setPaidAt(rs.getObject("paid_at", OffsetDateTime.class));
                    p.setMethodCode(rs.getString("method_code"));
                    p.setMethodName(rs.getString("method_name"));
                    list.add(p);
                }
            }
        }
        return list;
    }

    /* =========================================================
       [C7 / 143] LÔ ĐÃ TRỪ CỦA ĐƠN (sau khi xác nhận)
       ========================================================= */
    public List<SalesLineAllocation> findAllocations(long invoiceId) throws SQLException {
        String sql = """
                                SELECT a.allocation_id, a.sales_line_id, a.lot_id, a.quantity, a.scanned_qty,
                       (SELECT COALESCE(SUM(m.quantity_change), 0) FROM stock_movements m
                        WHERE m.lot_id = a.lot_id) AS lot_stock,
                       v.variant_name, pk.weight_g, g.is_whole_bean,
                       lo.lot_code, lo.expiry_date,
                       lo.expiry_date <= (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
                           + COALESCE((SELECT setting_value::int FROM system_settings
                                       WHERE setting_key = 'inventory.near_expiry_days'), 30) AS near_expiry
                FROM sales_line_allocations a
                JOIN sales_invoice_lines l ON l.sales_line_id = a.sales_line_id
                JOIN lots lo               ON lo.lot_id = a.lot_id
                JOIN packages pk           ON pk.package_id = l.package_id
                JOIN product_variants v    ON v.variant_id = pk.variant_id
                JOIN grind_sizes g         ON g.grind_size_id = pk.grind_size_id
                WHERE l.sales_invoice_id = ?
                ORDER BY l.sales_line_id, lo.expiry_date, lo.lot_id
                """;
        List<SalesLineAllocation> list = new ArrayList<>();
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SalesLineAllocation a = new SalesLineAllocation();
                    a.setAllocationId(rs.getLong("allocation_id"));
                    a.setSalesLineId(rs.getLong("sales_line_id"));
                    a.setLotId(rs.getLong("lot_id"));
                    a.setQuantity(rs.getInt("quantity"));
                    a.setVariantName(rs.getString("variant_name"));
                    a.setWeightG(rs.getInt("weight_g"));
                    a.setWholeBean(rs.getBoolean("is_whole_bean"));
                    a.setLotCode(rs.getString("lot_code"));
                    a.setExpiryDate(rs.getObject("expiry_date", LocalDate.class));
                    a.setNearExpiry(rs.getBoolean("near_expiry"));
                                        a.setScannedQty(rs.getInt("scanned_qty"));
                    a.setLotStock(rs.getInt("lot_stock"));
                    list.add(a);
                }
            }
        }
        return list;
    }

    /* =========================================================
       [138 + 140 + 142] THU TIỀN MẶT + XÁC NHẬN ĐƠN + TRỪ KHO FEFO
       Tất cả trong 1 transaction: lỗi ở bất kỳ bước nào thì không ghi gì cả.
       cashGiven = tiền khách đưa (null nếu đã thu đủ bằng cách khác, VD VNPay).
       ========================================================= */
        public void confirmOrder(long invoiceId, int userId, BigDecimal cashGiven) throws SQLException {
        try (Connection con = connect()) {
            con.setAutoCommit(false);
            try {
                confirmInTransaction(con, invoiceId, userId, cashGiven);
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                // Trigger no_negative_stock báo "không đủ tồn" khi 2 quầy bán cùng lô cùng lúc
                if (e instanceof SQLException && e.getMessage() != null && e.getMessage().contains("không đủ tồn")) {
                    throw new IllegalArgumentException("Kho vừa thay đổi, không đủ tồn. Tải lại đơn rồi xác nhận lại.");
                }
                throw e;
            }
        }
    }

    /**
     * Các bước xác nhận đơn, chạy trong transaction do nơi gọi mở (không commit ở đây).
     * Dùng chung cho: nhân viên bấm Xác nhận (tiền mặt) và VNPay báo thanh toán thành công.
     * userId = null khi hệ thống tự xác nhận (VNPay).
     */
    public void confirmInTransaction(Connection con, long invoiceId, Integer userId, BigDecimal cashGiven)
            throws SQLException {
        // 1. Khóa đơn để 2 máy không xác nhận cùng lúc, kiểm tra còn PENDING
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT status FROM sales_invoices WHERE sales_invoice_id = ? FOR UPDATE")) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy đơn");
                if (!"PENDING".equals(rs.getString(1))) {
                    throw new IllegalArgumentException("Đơn đã xác nhận hoặc đã hủy");
                }
            }
        }

        // 2. Các dòng cần trừ (142.1)
        List<long[]> lines = new ArrayList<>();          // {sales_line_id, package_id, quantity}
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT sales_line_id, package_id, quantity FROM sales_invoice_lines "
                + "WHERE sales_invoice_id = ? ORDER BY package_id")) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3)});
                }
            }
        }
        if (lines.isEmpty()) throw new IllegalArgumentException("Đơn chưa có gói nào");

        // 3. Thu tiền mặt phần còn thiếu (138). Ghi đúng số còn thiếu, phần dư là tiền thối.
        BigDecimal due = dueAmount(con, invoiceId);
        if (due.signum() > 0) {
            if (cashGiven == null || cashGiven.compareTo(due) < 0) {
                throw new IllegalArgumentException("Khách đưa chưa đủ, còn thiếu " + money(due));
            }
            if (userId == null) throw new IllegalArgumentException("Thu tiền mặt phải có nhân viên xác nhận");
            String sqlPay = """
                    INSERT INTO payments (sales_invoice_id, payment_method_id, amount, status,
                                          payment_kind, confirmed_by, paid_at)
                    VALUES (?, (SELECT payment_method_id FROM payment_methods
                                WHERE method_code = 'CASH' AND is_active),
                            ?, 'SUCCESS', 'CHARGE', ?, now())
                    """;
            try (PreparedStatement ps = con.prepareStatement(sqlPay)) {
                ps.setLong(1, invoiceId);
                ps.setBigDecimal(2, due);
                ps.setInt(3, userId);
                ps.executeUpdate();
            }
        }

                // 4. Trừ kho đúng các lô đã chọn / đã quét (142, bán theo mã lô)
        lotDao.deductLots(con, invoiceId, userId);

        // 5. Chuyển đơn sang đã xác nhận (140.1)
        try (PreparedStatement ps = con.prepareStatement(
                                "UPDATE sales_invoices SET status = 'PAID', confirmed_at = now() WHERE sales_invoice_id = ? AND status = 'PENDING'")) {
            ps.setLong(1, invoiceId);
            ps.executeUpdate();
        }
        
        // 6. QR VNPay còn chờ của đơn (khách đổi sang trả tiền mặt) -> hủy, tránh treo giao dịch
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE payments SET status = 'CANCELLED' WHERE sales_invoice_id = ? AND status = 'PENDING'")) {
            ps.setLong(1, invoiceId);
            ps.executeUpdate();
        }
    }

    /* =========================================================
       [135] HỦY ĐƠN CHƯA XÁC NHẬN
       Chưa trừ kho nên không hoàn kho; QR VNPay đang chờ bị hủy theo.
       ========================================================= */
    public void cancelPendingOrder(long invoiceId, int userId, String reason) throws SQLException {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Chọn lý do hủy");

        try (Connection con = connect()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT status FROM sales_invoices WHERE sales_invoice_id = ? FOR UPDATE")) {
                    ps.setLong(1, invoiceId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy đơn");
                        if (!"PENDING".equals(rs.getString(1))) {
                            throw new IllegalArgumentException("Chỉ hủy được đơn chưa xác nhận");
                        }
                    }
                }

                // Đã thu tiền (VD khách chuyển khoản 1 phần) thì không hủy ở quầy: phải hoàn tiền, cần chủ
                BigDecimal paid = paidAmount(con, invoiceId);
                if (paid.signum() > 0) {
                    throw new IllegalArgumentException("Đơn đã thu " + money(paid)
                            + ", không hủy ở quầy được. Báo chủ cửa hàng xử lý hoàn tiền.");
                }
                                // Bỏ các lô đã chọn (chưa trừ kho)
                lotDao.releaseLots(con, invoiceId);
                // 135.2 hủy QR VNPay còn chờ
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE payments SET status = 'CANCELLED' WHERE sales_invoice_id = ? AND status = 'PENDING'")) {
                    ps.setLong(1, invoiceId);
                    ps.executeUpdate();
                }

                // 135.1 hủy đơn (chưa trừ kho -> is_restocked = false)
                try (PreparedStatement ps = con.prepareStatement("""
                        UPDATE sales_invoices
                        SET status = 'CANCELLED', cancelled_by = ?, cancelled_at = now(),
                            cancel_reason = ?, is_restocked = false
                        WHERE sales_invoice_id = ? AND status = 'PENDING'
                        """)) {
                    ps.setInt(1, userId);
                    ps.setString(2, reason.trim());
                    ps.setLong(3, invoiceId);
                    ps.executeUpdate();
                }

                writeAudit(con, userId, "CANCEL_PENDING_INVOICE", invoiceId, reason.trim());
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /* =========================================================
       [145] GHI NHẬN XUẤT HÓA ĐƠN (mỗi lần bấm In ở màn bán hàng)
       ========================================================= */
    public void logExportInvoice(long invoiceId, int userId) throws SQLException {
        try (Connection con = connect()) {
            writeAudit(con, userId, "EXPORT_INVOICE", invoiceId, null);
        }
    }

    /* =========================================================
       CHỈ SỐ TRONG NGÀY + THÔNG TIN IN HÓA ĐƠN
       Nhân viên chỉ xem thu trong ngày: tiền thu - tiền hoàn của giao dịch SUCCESS hôm nay.
       ========================================================= */
    public Map<String, Object> findOverview() throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        String sqlIncome = """
                SELECT COALESCE(SUM(CASE WHEN p.payment_kind = 'CHARGE' THEN p.amount ELSE -p.amount END)
                                FILTER (WHERE pm.method_code = 'CASH'), 0)  AS cash_today,
                       COALESCE(SUM(CASE WHEN p.payment_kind = 'CHARGE' THEN p.amount ELSE -p.amount END)
                                FILTER (WHERE pm.method_code <> 'CASH'), 0) AS online_today,
                       COALESCE(SUM(CASE WHEN p.payment_kind = 'CHARGE' THEN p.amount ELSE -p.amount END), 0) AS income_today,
                       COUNT(DISTINCT p.sales_invoice_id) FILTER (WHERE si.status = 'PAID') AS confirmed_today
                FROM payments p
                JOIN payment_methods pm ON pm.payment_method_id = p.payment_method_id
                JOIN sales_invoices si  ON si.sales_invoice_id = p.sales_invoice_id
                WHERE p.status = 'SUCCESS'
                  AND p.paid_at >= ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date)::timestamp
                                   AT TIME ZONE 'Asia/Ho_Chi_Minh'
                """;
        try (Connection con = connect()) {
            try (PreparedStatement ps = con.prepareStatement(sqlIncome);
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                result.put("cashToday", rs.getBigDecimal("cash_today"));
                result.put("onlineToday", rs.getBigDecimal("online_today"));
                result.put("incomeToday", rs.getBigDecimal("income_today"));
                result.put("confirmedToday", rs.getInt("confirmed_today"));
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COUNT(*) FROM sales_invoices WHERE status = 'PENDING'");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                result.put("pendingCount", rs.getInt(1));
            }
                        // VNPay đang bật? (trang cấu hình 163) -> màn bán hàng khóa nút QR khi tắt
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COALESCE(bool_or(is_active), false) FROM payment_methods WHERE method_code = 'VNPAY'");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                result.put("vnpayActive", rs.getBoolean(1));
            }
            // Thông tin in trên hóa đơn (C6)
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT setting_key, setting_value FROM system_settings WHERE setting_key LIKE 'invoice.%'");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // invoice.store_name -> storeName ...
                    String key = rs.getString(1).substring("invoice.".length());
                    result.put(toCamel(key), rs.getString(2));
                }
            }
        }
        return result;
    }

    /* =========================================================
       NGƯỜI THAO TÁC (khi chưa có đăng nhập)
       ========================================================= */

    /** Tài khoản quầy (STAFF) đang hoạt động, không có thì lấy tài khoản đầu tiên. */
    public Integer findDefaultUserId() throws SQLException {
        String sql = """
                SELECT u.user_id
                FROM users u JOIN roles r ON r.role_id = u.role_id
                WHERE u.status = 'ACTIVE'
                ORDER BY (r.role_code = 'STAFF') DESC, u.user_id
                LIMIT 1
                """;
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : null;
        }
    }

    /* =========================================================
       HÀM PHỤ
       ========================================================= */

    /** C2: đơn phải tồn tại và còn PENDING thì mới được sửa */
    private void requirePending(Connection con, long invoiceId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT status FROM sales_invoices WHERE sales_invoice_id = ?")) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy đơn");
                if (!"PENDING".equals(rs.getString(1))) {
                    throw new IllegalArgumentException("Đơn đã xác nhận hoặc đã hủy, không sửa được");
                }
            }
        }
    }

    private int sellableQty(Connection con, int packageId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SQL_SELLABLE_QTY)) {
            ps.setInt(1, packageId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("qty");
            }
        }
    }

    /** Đã thu thực tế = thu - hoàn (giao dịch SUCCESS) */
    private BigDecimal paidAmount(Connection con, long invoiceId) throws SQLException {
        String sql = """
                SELECT COALESCE(SUM(CASE WHEN payment_kind = 'CHARGE' THEN amount ELSE -amount END), 0)
                FROM payments WHERE sales_invoice_id = ? AND status = 'SUCCESS'
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    /** Còn phải thu = tổng tiền - đã thu (C8) */
    private BigDecimal dueAmount(Connection con, long invoiceId) throws SQLException {
        BigDecimal total;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COALESCE(SUM(quantity * unit_price), 0) FROM sales_invoice_lines WHERE sales_invoice_id = ?")) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                total = rs.getBigDecimal(1);
            }
        }
        return total.subtract(paidAmount(con, invoiceId));
    }

    private void writeAudit(Connection con, int userId, String action, long invoiceId, String reason) throws SQLException {
        String sql = """
                INSERT INTO audit_logs (user_id, action, entity_type, entity_id, detail)
                VALUES (?, ?, 'sales_invoices', ?, CASE WHEN ? IS NULL THEN NULL ELSE jsonb_build_object('reason', ?) END)
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, action);
            ps.setLong(3, invoiceId);
            ps.setString(4, reason);
            ps.setString(5, reason);
            ps.executeUpdate();
        }
    }

    private String packageName(Connection con, int packageId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT v.variant_name || ' ' || pk.weight_g || 'g' FROM packages pk
                JOIN product_variants v ON v.variant_id = pk.variant_id WHERE pk.package_id = ?
                """)) {
            ps.setInt(1, packageId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "#" + packageId;
            }
        }
    }

    private static String money(BigDecimal value) {
        return String.format("%,.0fđ", value).replace(',', '.');
    }

    /** store_name -> storeName */
    private static String toCamel(String key) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : key.toCharArray()) {
            if (c == '_') { upper = true; continue; }
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return sb.toString();
    }

    private Connection connect() throws SQLException {
        Connection con = DBConnect.getConnection();
        if (con == null) throw new SQLException("Không kết nối được database (xem log Tomcat)");
        return con;
    }
}