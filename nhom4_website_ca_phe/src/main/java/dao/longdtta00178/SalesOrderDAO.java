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

import models.enums.SalesInvoiceStatus;
import models.longdtta00178.SalesInvoice;
import models.longdtta00178.SalesInvoiceLine;
import uri.DBConnect;

/**
 * Đơn đang lập ở màn bán hàng.
 * UC 130 tạo đơn, 132 thêm gói / đổi số lượng, 134 xóa gói, 136 giới hạn theo tồn còn hạn.
 * Lỗi nghiệp vụ (đơn đã đóng, vượt tồn, chưa có giá...) ném IllegalArgumentException
 * -> controller trả mã 400 kèm câu báo lỗi cho nhân viên.
 */
public class SalesOrderDAO {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

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
                       (SELECT COALESCE(SUM(p.amount), 0)
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

    private Connection connect() throws SQLException {
        Connection con = DBConnect.getConnection();
        if (con == null) throw new SQLException("Không kết nối được database (xem log Tomcat)");
        return con;
    }
}