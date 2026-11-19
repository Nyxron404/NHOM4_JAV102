package dao.longdtta00178;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import uri.DBConnect;

/**
 * Bán theo MÃ LÔ (thay cho FEFO tự động).
 *
 * Đơn đang lập: mỗi lần thêm 1 túi là thêm vào đúng 1 lô -> lưu ở sales_line_allocations
 *   (chưa trừ kho). scanned_qty = số túi trong đó được QUÉT QR (máy quét / camera).
 * Xác nhận đơn: trừ kho đúng các lô đã chọn (deductLots).
 *   - Lô hết hạn: luôn chặn.
 *   - Lô không đủ tồn: phần vượt phải nằm trong số túi đã quét -> cho bán âm,
 *     tạo / cập nhật phiếu điều chỉnh chờ chủ duyệt (stock_counts ADJUSTMENT, ghi chú [BAN_AM]).
 *     Chọn lô bằng tay thì không được vượt tồn.
 */
public class SalesLotDAO {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final String NEGATIVE_NOTE = "[BAN_AM]";

    /** Kết quả thêm 1 túi: cảnh báo (nếu có) để màn bán hàng hiện cho nhân viên */
    public record AddResult(long invoiceId, String warning) {}

    /* =========================================================
       THÊM 1 TÚI THEO LÔ
       lotId hoặc lotCode (quét / gõ mã lô). scanned = true khi đọc bằng máy quét / camera.
       ========================================================= */
    public AddResult addLot(long invoiceId, Long lotId, String lotCode, boolean scanned) throws SQLException {
        try (Connection con = connect()) {
            con.setAutoCommit(false);
            try {
                requirePending(con, invoiceId);

                // 1. Lô + gói + giá hiện hành
                String sql = """
                        SELECT lo.lot_id, lo.lot_code, lo.expiry_date, pil.package_id,
                               pk.status AS pkg_status, v.status AS variant_status, pk.sku, cp.sale_price,
                               (SELECT COALESCE(SUM(m.quantity_change), 0) FROM stock_movements m
                                WHERE m.lot_id = lo.lot_id) AS stock
                        FROM lots lo
                        JOIN purchase_invoice_lines pil ON pil.purchase_line_id = lo.purchase_line_id
                        JOIN packages pk                ON pk.package_id = pil.package_id
                        JOIN product_variants v         ON v.variant_id = pk.variant_id
                        LEFT JOIN v_package_current_price cp ON cp.package_id = pk.package_id
                        WHERE (CAST(? AS BIGINT) IS NOT NULL AND lo.lot_id = CAST(? AS BIGINT))
                           OR (CAST(? AS BIGINT) IS NULL AND upper(lo.lot_code) = upper(?))
                        """;
                long foundLotId;
                int packageId, stock;
                String code;
                BigDecimal price;
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setObject(1, lotId, Types.BIGINT);
                    ps.setObject(2, lotId, Types.BIGINT);
                    ps.setObject(3, lotId, Types.BIGINT);
                    ps.setString(4, lotCode == null ? null : lotCode.trim());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Không tìm thấy lô"
                                    + (lotCode != null ? " " + lotCode.trim() : ""));
                        }
                        foundLotId = rs.getLong("lot_id");
                        code = rs.getString("lot_code");
                        packageId = rs.getInt("package_id");
                        stock = rs.getInt("stock");
                        price = rs.getBigDecimal("sale_price");
                        if (!"ACTIVE".equals(rs.getString("pkg_status")) || !"ACTIVE".equals(rs.getString("variant_status"))) {
                            throw new IllegalArgumentException("Gói " + rs.getString("sku") + " đã ngừng bán");
                        }
                        if (price == null) throw new IllegalArgumentException("Gói " + rs.getString("sku") + " chưa có giá bán");
                        if (rs.getObject("expiry_date", LocalDate.class).isBefore(LocalDate.now(VIETNAM))) {
                            throw new IllegalArgumentException("Lô " + code + " đã hết hạn, không bán");
                        }
                    }
                }

                // 2. Số túi lô này đã có trong đơn
                int inOrder = 0;
                try (PreparedStatement ps = con.prepareStatement("""
                        SELECT COALESCE(SUM(a.quantity), 0) FROM sales_line_allocations a
                        JOIN sales_invoice_lines l ON l.sales_line_id = a.sales_line_id
                        WHERE l.sales_invoice_id = ? AND a.lot_id = ?
                        """)) {
                    ps.setLong(1, invoiceId);
                    ps.setLong(2, foundLotId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        inOrder = rs.getInt(1);
                    }
                }
                String warning = null;
                if (inOrder + 1 > stock) {
                    if (!scanned) {
                        throw new IllegalArgumentException(stock <= 0
                                ? "Lô " + code + " đã hết hàng trên hệ thống. Quét QR trên túi nếu có hàng thật."
                                : "Lô " + code + " chỉ còn " + stock + " túi, trong đơn đã có " + inOrder);
                    }
                    warning = "Lô " + code + " vượt tồn hệ thống (còn " + Math.max(stock, 0)
                            + "). Xác nhận đơn sẽ bán âm và gửi chủ cửa hàng duyệt điều chỉnh kho.";
                }

                // 3. Dòng hàng theo gói (giá lấy lúc thêm lần đầu), cộng 1 túi
                long lineId;
                try (PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO sales_invoice_lines (sales_invoice_id, package_id, quantity, unit_price)
                        VALUES (?, ?, 1, ?)
                        ON CONFLICT (sales_invoice_id, package_id)
                        DO UPDATE SET quantity = sales_invoice_lines.quantity + 1
                        RETURNING sales_line_id
                        """)) {
                    ps.setLong(1, invoiceId);
                    ps.setInt(2, packageId);
                    ps.setBigDecimal(3, price);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        lineId = rs.getLong(1);
                    }
                }

                // 4. Lô của dòng, cộng 1 túi (quét thì cộng cả scanned_qty)
                try (PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO sales_line_allocations (sales_line_id, lot_id, quantity, scanned_qty)
                        VALUES (?, ?, 1, ?)
                        ON CONFLICT (sales_line_id, lot_id)
                        DO UPDATE SET quantity = sales_line_allocations.quantity + 1,
                                      scanned_qty = sales_line_allocations.scanned_qty + EXCLUDED.scanned_qty
                        """)) {
                    ps.setLong(1, lineId);
                    ps.setLong(2, foundLotId);
                    ps.setInt(3, scanned ? 1 : 0);
                    ps.executeUpdate();
                }

                con.commit();
                return new AddResult(invoiceId, warning);
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /* =========================================================
       ĐỔI SỐ TÚI CỦA 1 LÔ TRONG DÒNG (0 = bỏ lô đó). Dòng hết lô thì xóa dòng.
       Tăng bằng tay: không vượt tồn. Giảm: bớt túi quét trước phần chọn tay sau cùng.
       ========================================================= */
    public void updateLotQty(long invoiceId, long lineId, long lotId, int quantity) throws SQLException {
        if (quantity < 0) throw new IllegalArgumentException("Số lượng không hợp lệ");
        try (Connection con = connect()) {
            con.setAutoCommit(false);
            try {
                requirePending(con, invoiceId);
                int current, scannedQty;
                try (PreparedStatement ps = con.prepareStatement("""
                        SELECT a.quantity, a.scanned_qty, lo.lot_code,
                               (SELECT COALESCE(SUM(m.quantity_change), 0) FROM stock_movements m
                                WHERE m.lot_id = a.lot_id) AS stock
                        FROM sales_line_allocations a
                        JOIN sales_invoice_lines l ON l.sales_line_id = a.sales_line_id
                        JOIN lots lo               ON lo.lot_id = a.lot_id
                        WHERE a.sales_line_id = ? AND a.lot_id = ? AND l.sales_invoice_id = ?
                        FOR UPDATE OF a
                        """)) {
                    ps.setLong(1, lineId);
                    ps.setLong(2, lotId);
                    ps.setLong(3, invoiceId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy lô trong đơn");
                        current = rs.getInt("quantity");
                        scannedQty = rs.getInt("scanned_qty");
                        int stock = rs.getInt("stock");
                        // phần chọn tay (không quét) không được vượt tồn
                        if (quantity > current && quantity - Math.min(scannedQty, quantity) > Math.max(stock, 0)) {
                            throw new IllegalArgumentException("Lô " + rs.getString("lot_code") + " chỉ còn "
                                    + Math.max(stock, 0) + " túi. Quét QR trên túi nếu có hàng thật.");
                        }
                    }
                }

                if (quantity == 0) {
                    exec(con, "DELETE FROM sales_line_allocations WHERE sales_line_id = ? AND lot_id = ?", lineId, lotId);
                } else {
                    try (PreparedStatement ps = con.prepareStatement("""
                            UPDATE sales_line_allocations SET quantity = ?, scanned_qty = LEAST(scanned_qty, ?)
                            WHERE sales_line_id = ? AND lot_id = ?
                            """)) {
                        ps.setInt(1, quantity);
                        ps.setInt(2, quantity);
                        ps.setLong(3, lineId);
                        ps.setLong(4, lotId);
                        ps.executeUpdate();
                    }
                }
                syncLine(con, lineId);
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** [134] Bỏ cả dòng: xóa lô đã chọn trước rồi xóa dòng */
    public void removeLine(long invoiceId, long lineId) throws SQLException {
        try (Connection con = connect()) {
            con.setAutoCommit(false);
            try {
                requirePending(con, invoiceId);
                exec(con, """
                        DELETE FROM sales_line_allocations a USING sales_invoice_lines l
                        WHERE a.sales_line_id = l.sales_line_id AND l.sales_line_id = ? AND l.sales_invoice_id = ?
                        """, lineId, invoiceId);
                if (exec(con, "DELETE FROM sales_invoice_lines WHERE sales_line_id = ? AND sales_invoice_id = ?",
                        lineId, invoiceId) == 0) {
                    throw new IllegalArgumentException("Không tìm thấy dòng hàng trong đơn");
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Hủy đơn chưa xác nhận: bỏ lô đã chọn (chưa trừ kho nên không hoàn kho) */
    public void releaseLots(Connection con, long invoiceId) throws SQLException {
        exec(con, """
                DELETE FROM sales_line_allocations a USING sales_invoice_lines l
                WHERE a.sales_line_id = l.sales_line_id AND l.sales_invoice_id = ?
                """, invoiceId);
    }

    /* =========================================================
       [142] TRỪ KHO THEO LÔ ĐÃ CHỌN (gọi trong transaction xác nhận đơn)
       userId null = hệ thống (VNPay tự xác nhận)
       ========================================================= */
    public void deductLots(Connection con, long invoiceId, Integer userId) throws SQLException {
        // 1. Mỗi dòng phải chọn đủ lô
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT pk.sku, l.quantity, COALESCE(SUM(a.quantity), 0) AS allocated
                FROM sales_invoice_lines l
                JOIN packages pk ON pk.package_id = l.package_id
                LEFT JOIN sales_line_allocations a ON a.sales_line_id = l.sales_line_id
                WHERE l.sales_invoice_id = ?
                GROUP BY l.sales_line_id, pk.sku, l.quantity
                HAVING COALESCE(SUM(a.quantity), 0) <> l.quantity
                """)) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalArgumentException("Gói " + rs.getString("sku")
                            + " chưa chọn đủ lô (" + rs.getInt("allocated") + "/" + rs.getInt("quantity")
                            + "). Bỏ gói rồi thêm lại theo lô.");
                }
            }
        }

        // 2. Gom số túi theo lô, khóa lô (thứ tự lot_id để 2 quầy không chờ nhau vòng tròn)
        record LotNeed(long lotId, String code, LocalDate expiry, int qty, int scanned) {}
        List<LotNeed> needs = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT a.lot_id, lo.lot_code, lo.expiry_date, SUM(a.quantity) AS qty, SUM(a.scanned_qty) AS scanned
                FROM sales_line_allocations a
                JOIN sales_invoice_lines l ON l.sales_line_id = a.sales_line_id
                JOIN lots lo               ON lo.lot_id = a.lot_id
                WHERE l.sales_invoice_id = ?
                GROUP BY a.lot_id, lo.lot_code, lo.expiry_date
                ORDER BY a.lot_id
                """)) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    needs.add(new LotNeed(rs.getLong("lot_id"), rs.getString("lot_code"),
                            rs.getObject("expiry_date", LocalDate.class), rs.getInt("qty"), rs.getInt("scanned")));
                }
            }
        }

        // 2b. Khóa phiếu [BAN_AM] đang chờ của các lô TRƯỚC khi khóa lô — cùng thứ tự với
        //     StockCountDAO.approve (phiếu -> lô). Không khóa thì chủ duyệt đúng lúc quầy đang bán âm
        //     sẽ cộng theo số "tồn hệ thống" cũ: duyệt xong lô vẫn âm mà không còn phiếu chờ.
        if (!needs.isEmpty()) {
            try (PreparedStatement ps = con.prepareStatement("""
                    SELECT c.stock_count_id
                    FROM stock_counts c
                    JOIN stock_count_lines cl ON cl.stock_count_id = c.stock_count_id
                    WHERE cl.lot_id = ANY (?) AND c.count_type = 'ADJUSTMENT'
                      AND c.status = 'PENDING' AND c.note LIKE ?
                    ORDER BY c.stock_count_id
                    FOR UPDATE OF c
                    """)) {
                ps.setArray(1, con.createArrayOf("bigint", needs.stream().map(LotNeed::lotId).toArray()));
                ps.setString(2, NEGATIVE_NOTE + "%");
                ps.executeQuery().close();
            }
        }

        // 3. Kiểm tra hạn + tồn; phần vượt tồn phải là túi đã quét
        LocalDate today = LocalDate.now(VIETNAM);
        Set<Long> negativeLots = new LinkedHashSet<>();
        for (LotNeed n : needs) {
            exec(con, "SELECT lot_id FROM lots WHERE lot_id = ? FOR UPDATE", n.lotId());
            if (n.expiry().isBefore(today)) {
                throw new IllegalArgumentException("Lô " + n.code() + " đã hết hạn, bỏ khỏi đơn rồi chọn lô khác");
            }
            int stock = lotStock(con, n.lotId());
            int over = n.qty() - Math.max(stock, 0);          // số túi bán vượt tồn
            if (over > 0) {
                if (over > n.scanned()) {
                    throw new IllegalArgumentException("Lô " + n.code() + " chỉ còn " + Math.max(stock, 0)
                            + " túi. Giảm số lượng, hoặc quét QR trên túi nếu có hàng thật.");
                }
                negativeLots.add(n.lotId());
            }
        }

        // 4. Cho phép âm đúng các lô đã quét (trigger no_negative_stock đọc biến này)
        if (!negativeLots.isEmpty()) {
            StringBuilder list = new StringBuilder(",");
            for (Long id : negativeLots) list.append(id).append(',');
            try (PreparedStatement ps = con.prepareStatement("SELECT set_config('app.allow_negative_lots', ?, true)")) {
                ps.setString(1, list.toString());
                ps.executeQuery().close();
            }
        }

        // 5. Ghi sổ kho
        for (LotNeed n : needs) {
            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO stock_movements (lot_id, movement_type, quantity_change, sales_invoice_id, performed_by)
                    VALUES (?, 'SALE', ?, ?, ?)
                    """)) {
                ps.setLong(1, n.lotId());
                ps.setInt(2, -n.qty());
                ps.setLong(3, invoiceId);
                ps.setObject(4, userId, Types.INTEGER);
                ps.executeUpdate();
            } catch (SQLException e) {
                if (e.getMessage() != null && e.getMessage().contains("không đủ tồn")) {
                    throw new IllegalArgumentException("Lô " + n.code()
                            + " không đủ tồn. Bán âm khi quét QR chưa được bật trong DB (trigger no_negative_stock).");
                }
                throw e;
            }
        }

        // 6. Lô bị âm -> phiếu điều chỉnh chờ chủ duyệt
        for (Long lotId : negativeLots) {
            requestNegativeAdjustment(con, invoiceId, lotId, userId);
        }
        if (!negativeLots.isEmpty()) {
            try (PreparedStatement ps = con.prepareStatement("SELECT set_config('app.allow_negative_lots', '', true)")) {
                ps.executeQuery().close();
            }
        }
    }

    /**
     * Mỗi lô âm có tối đa 1 phiếu [BAN_AM] đang chờ duyệt. Đã có thì cập nhật "tồn hệ thống" = tồn hiện tại,
     * để chủ duyệt xong (approve ghi counted - system) tồn về đúng 0.
     */
    private void requestNegativeAdjustment(Connection con, long invoiceId, long lotId, Integer userId)
            throws SQLException {
        int stock = lotStock(con, lotId);
        int updated;
        try (PreparedStatement ps = con.prepareStatement("""
                UPDATE stock_count_lines cl SET system_qty = ?
                FROM stock_counts c
                WHERE c.stock_count_id = cl.stock_count_id AND cl.lot_id = ?
                  AND c.count_type = 'ADJUSTMENT' AND c.status = 'PENDING' AND c.note LIKE ?
                """)) {
            ps.setInt(1, stock);
            ps.setLong(2, lotId);
            ps.setString(3, NEGATIVE_NOTE + "%");
            updated = ps.executeUpdate();
        }
        if (updated > 0) return;

        String invoiceNo, lotCode;
        int createdBy;
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT si.invoice_no, si.created_by, lo.lot_code
                FROM sales_invoices si, lots lo WHERE si.sales_invoice_id = ? AND lo.lot_id = ?
                """)) {
            ps.setLong(1, invoiceId);
            ps.setLong(2, lotId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                invoiceNo = rs.getString("invoice_no");
                createdBy = rs.getInt("created_by");
                lotCode = rs.getString("lot_code");
            }
        }
        String note = NEGATIVE_NOTE + " Lô " + lotCode + " bán vượt tồn khi quét QR (đơn " + invoiceNo
                + "). Duyệt để đưa tồn về 0.";
        long countId;
        try (PreparedStatement ps = con.prepareStatement("""
                INSERT INTO stock_counts (count_type, status, created_by, note)
                VALUES ('ADJUSTMENT', 'PENDING', ?, ?) RETURNING stock_count_id
                """)) {
            ps.setInt(1, userId != null ? userId : createdBy);
            ps.setString(2, note);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                countId = rs.getLong(1);
            }
        }
        try (PreparedStatement ps = con.prepareStatement("""
                INSERT INTO stock_count_lines (stock_count_id, lot_id, system_qty, counted_qty, note)
                VALUES (?, ?, ?, 0, ?)
                """)) {
            ps.setLong(1, countId);
            ps.setLong(2, lotId);
            ps.setInt(3, stock);
            ps.setString(4, "Bán âm khi quét QR");
            ps.executeUpdate();
        }
    }

    /* ---------- Hàm phụ ---------- */

    /** Số túi của dòng = tổng các lô; không còn lô thì xóa dòng */
    private void syncLine(Connection con, long lineId) throws SQLException {
        int total;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COALESCE(SUM(quantity), 0) FROM sales_line_allocations WHERE sales_line_id = ?")) {
            ps.setLong(1, lineId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                total = rs.getInt(1);
            }
        }
        if (total == 0) {
            exec(con, "DELETE FROM sales_invoice_lines WHERE sales_line_id = ?", lineId);
        } else {
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE sales_invoice_lines SET quantity = ? WHERE sales_line_id = ?")) {
                ps.setInt(1, total);
                ps.setLong(2, lineId);
                ps.executeUpdate();
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

    private void requirePending(Connection con, long invoiceId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT status FROM sales_invoices WHERE sales_invoice_id = ? FOR UPDATE")) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy đơn");
                if (!"PENDING".equals(rs.getString(1))) {
                    throw new IllegalArgumentException("Đơn đã xác nhận hoặc đã hủy, không sửa được");
                }
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

    private Connection connect() throws SQLException {
        Connection con = DBConnect.getConnection();
        if (con == null) throw new SQLException("Không kết nối được database (xem log Tomcat)");
        return con;
    }
}