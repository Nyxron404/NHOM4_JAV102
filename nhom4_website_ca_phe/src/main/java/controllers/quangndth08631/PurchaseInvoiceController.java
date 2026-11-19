package controllers.quangndth08631;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import uri.DBConnect;

@WebServlet(name = "PurchaseInvoiceController", urlPatterns = {"/api/purchase-invoices/*"})
public class PurchaseInvoiceController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();

        try (PrintWriter out = resp.getWriter()) {
            if (path == null || "/".equals(path)) {
                // Xem danh sách, tìm kiếm, lọc theo ngày, nhà cung cấp, trạng thái
                out.print(searchInvoices(
                    req.getParameter("status"),
                    req.getParameter("q"),
                    req.getParameter("supplierId"),
                    req.getParameter("fromDate"),
                    req.getParameter("toDate")
                ));
            } else if ("/detail".equals(path)) {
                out.print(getInvoiceDetail(Long.parseLong(req.getParameter("id"))));
            } else if ("/check-duplicate".equals(path)) {
                int supplierId = Integer.parseInt(req.getParameter("supplierId"));
                String invoiceNo = req.getParameter("invoiceNo");
                out.print("{\"duplicate\":" + isDuplicateInvoice(supplierId, invoiceNo) + "}");
            } else if ("/auto-match".equals(path)) {
                int supplierId = Integer.parseInt(req.getParameter("supplierId"));
                String itemCode = req.getParameter("itemCode");
                out.print(autoMatchItem(supplierId, itemCode));
            } else if ("/mappings".equals(path)) {
                out.print(listMappings());
            } else if ("/lots".equals(path)) {
                out.print(listLots());
            } else if ("/lookups".equals(path)) {
                // Lấy danh sách NCC và Gói bán (packages) cho dropdown giao diện
                out.print(getFormLookups());
            } else {
                resp.setStatus(404);
                out.print("{\"error\":\"Endpoint không tồn tại\"}");
            }
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().print("{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();

        HttpSession session = req.getSession(false);
        int userId = (session != null && session.getAttribute("userId") != null)
                ? (int) session.getAttribute("userId") : 1;

        try (PrintWriter out = resp.getWriter()) {
            switch (path != null ? path : "") {
                case "/create": {
                    int supplierId = Integer.parseInt(req.getParameter("supplierId"));
                    String invoiceNo = req.getParameter("invoiceNo");
                    String lookupCode = req.getParameter("lookupCode");
                    Date invoiceDate = Date.valueOf(req.getParameter("invoiceDate"));
                    String inputMethod = req.getParameter("inputMethod"); // MANUAL | LOOKUP | QR
                    if (isDuplicateInvoice(supplierId, invoiceNo)) {
                        throw new Exception("Số hóa đơn " + invoiceNo + " của nhà cung cấp này đã tồn tại!");
                    }
                    long id = createInvoice(supplierId, invoiceNo, lookupCode, invoiceDate, inputMethod, userId);
                    out.print("{\"success\":true,\"id\":" + id + "}");
                    break;
                }
                case "/add-line": {
                    long invoiceId = Long.parseLong(req.getParameter("purchaseInvoiceId"));
                    int packageId = Integer.parseInt(req.getParameter("packageId"));
                    String supItemCode = req.getParameter("supplierItemCode");
                    String supItemName = req.getParameter("supplierItemName");
                    int qty = Integer.parseInt(req.getParameter("quantity"));
                    long unitCost = Long.parseLong(req.getParameter("unitCost"));
                    String lotCode = req.getParameter("lotCode");
                    Date roastDate = parseDate(req.getParameter("roastDate"));
                    Date expiryDate = Date.valueOf(req.getParameter("expiryDate"));

                    addLineAndLot(invoiceId, packageId, supItemCode, supItemName, qty, unitCost, lotCode, roastDate, expiryDate);
                    out.print("{\"success\":true}");
                    break;
                }
                case "/update-line": {
                    long lineId = Long.parseLong(req.getParameter("lineId"));
                    int qty = Integer.parseInt(req.getParameter("quantity"));
                    long unitCost = Long.parseLong(req.getParameter("unitCost"));
                    Date roastDate = parseDate(req.getParameter("roastDate"));
                    Date expiryDate = Date.valueOf(req.getParameter("expiryDate"));
                    updateLineAndLot(lineId, qty, unitCost, roastDate, expiryDate);
                    out.print("{\"success\":true}");
                    break;
                }
                case "/delete-line": {
                    long lineId = Long.parseLong(req.getParameter("lineId"));
                    deleteLineAndLot(lineId);
                    out.print("{\"success\":true}");
                    break;
                }
                case "/create-draft-variant": {
                    // Tự tạo biến thể nháp (DRAFT) + gói bán mới khi gặp hàng mới từ hóa đơn NCC
                    String itemName = req.getParameter("itemName");
                    int weightG = Integer.parseInt(req.getParameter("weightG"));
                    int newPkgId = createDraftVariantAndPackage(itemName, weightG);
                    out.print("{\"success\":true,\"packageId\":" + newPkgId + "}");
                    break;
                }
                case "/confirm": {
                    long invoiceId = Long.parseLong(req.getParameter("id"));
                    confirmInvoice(invoiceId, userId);
                    out.print("{\"success\":true,\"message\":\"Đã xác nhận nhập kho, cộng tồn và ghi nhận khoản chi!\"}");
                    break;
                }
                case "/cancel": {
                    long invoiceId = Long.parseLong(req.getParameter("id"));
                    cancelInvoice(invoiceId);
                    out.print("{\"success\":true,\"message\":\"Đã hủy phiên nhập!\"}");
                    break;
                }
                case "/lots/update-roast": {
                    long lotId = Long.parseLong(req.getParameter("lotId"));
                    Date roastDate = parseDate(req.getParameter("roastDate"));
                    updateLotRoastDate(lotId, roastDate);
                    out.print("{\"success\":true}");
                    break;
                }
                case "/lots/update-expiry": {
                    long lotId = Long.parseLong(req.getParameter("lotId"));
                    Date expiryDate = Date.valueOf(req.getParameter("expiryDate"));
                    updateLotExpiryDate(lotId, expiryDate);
                    out.print("{\"success\":true}");
                    break;
                }
                case "/mappings/save": {
                    String mappingIdStr = req.getParameter("mappingId");
                    int supplierId = Integer.parseInt(req.getParameter("supplierId"));
                    String supplierItemCode = req.getParameter("supplierItemCode");
                    int packageId = Integer.parseInt(req.getParameter("packageId"));
                    saveMapping(mappingIdStr, supplierId, supplierItemCode, packageId);
                    out.print("{\"success\":true}");
                    break;
                }
                case "/mappings/delete": {
                    int mappingId = Integer.parseInt(req.getParameter("mappingId"));
                    deleteMapping(mappingId);
                    out.print("{\"success\":true}");
                    break;
                }
                default:
                    resp.setStatus(404);
                    out.print("{\"error\":\"Action không hợp lệ\"}");
            }
        } catch (Exception e) {
            resp.setStatus(400);
            resp.getWriter().print("{\"success\":false,\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ==================== LOGIC XỬ LÝ DATABASE ====================

    private String searchInvoices(String status, String q, String supplierId, String fromDate, String toDate) throws Exception {
        StringBuilder sql = new StringBuilder(
            "SELECT pi.purchase_invoice_id, pi.invoice_no, pi.lookup_code, pi.invoice_date, pi.input_method, pi.status, " +
            "s.supplier_id, s.supplier_name, COALESCE(v.total_amount, 0) AS total_amount " +
            "FROM purchase_invoices pi " +
            "JOIN suppliers s ON s.supplier_id = pi.supplier_id " +
            "LEFT JOIN v_purchase_invoice_totals v ON v.purchase_invoice_id = pi.purchase_invoice_id WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isEmpty() && !"ALL".equals(status)) {
            sql.append("AND pi.status = ? "); params.add(status);
        }
        if (supplierId != null && !supplierId.isEmpty() && !"ALL".equals(supplierId)) {
            sql.append("AND pi.supplier_id = ? "); params.add(Integer.parseInt(supplierId));
        }
        if (fromDate != null && !fromDate.isEmpty()) {
            sql.append("AND pi.invoice_date >= ? "); params.add(Date.valueOf(fromDate));
        }
        if (toDate != null && !toDate.isEmpty()) {
            sql.append("AND pi.invoice_date <= ? "); params.add(Date.valueOf(toDate));
        }
        if (q != null && !q.trim().isEmpty()) {
            sql.append("AND (pi.invoice_no ILIKE ? OR pi.lookup_code ILIKE ? OR s.supplier_name ILIKE ?) ");
            String kw = "%" + q.trim() + "%";
            params.add(kw); params.add(kw); params.add(kw);
        }
        sql.append("ORDER BY pi.created_at DESC");

        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DBConnect.getConnection(); PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{")
                        .append("\"id\":").append(rs.getLong("purchase_invoice_id")).append(",")
                        .append("\"invoiceNo\":\"").append(esc(rs.getString("invoice_no"))).append("\",")
                        .append("\"lookupCode\":\"").append(esc(rs.getString("lookup_code"))).append("\",")
                        .append("\"invoiceDate\":\"").append(rs.getDate("invoice_date")).append("\",")
                        .append("\"inputMethod\":\"").append(rs.getString("input_method")).append("\",")
                        .append("\"status\":\"").append(rs.getString("status")).append("\",")
                        .append("\"supplierId\":").append(rs.getInt("supplier_id")).append(",")
                        .append("\"supplierName\":\"").append(esc(rs.getString("supplier_name"))).append("\",")
                        .append("\"totalAmount\":").append(rs.getLong("total_amount"))
                        .append("}");
                }
            }
        }
        return json.append("]").toString();
    }

    private String getInvoiceDetail(long invoiceId) throws Exception {
        String sql = "SELECT pil.purchase_line_id, pil.line_no, pil.package_id, pil.supplier_item_code, pil.supplier_item_name, " +
                     "p.sku, pv.variant_name, pil.quantity, pil.unit_cost, l.lot_id, l.lot_code, l.roast_date, l.expiry_date " +
                     "FROM purchase_invoice_lines pil " +
                     "JOIN packages p ON p.package_id = pil.package_id " +
                     "JOIN product_variants pv ON pv.variant_id = p.variant_id " +
                     "LEFT JOIN lots l ON l.purchase_line_id = pil.purchase_line_id " +
                     "WHERE pil.purchase_invoice_id = ? ORDER BY pil.line_no";
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DBConnect.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{")
                        .append("\"lineId\":").append(rs.getLong("purchase_line_id")).append(",")
                        .append("\"lineNo\":").append(rs.getInt("line_no")).append(",")
                        .append("\"packageId\":").append(rs.getInt("package_id")).append(",")
                        .append("\"supplierItemCode\":\"").append(esc(rs.getString("supplier_item_code"))).append("\",")
                        .append("\"sku\":\"").append(esc(rs.getString("sku"))).append("\",")
                        .append("\"variantName\":\"").append(esc(rs.getString("variant_name"))).append("\",")
                        .append("\"quantity\":").append(rs.getInt("quantity")).append(",")
                        .append("\"unitCost\":").append(rs.getLong("unit_cost")).append(",")
                        .append("\"lotId\":").append(rs.getLong("lot_id")).append(",")
                        .append("\"lotCode\":\"").append(esc(rs.getString("lot_code"))).append("\",")
                        .append("\"roastDate\":\"").append(rs.getDate("roast_date") != null ? rs.getDate("roast_date") : "").append("\",")
                        .append("\"expiryDate\":\"").append(rs.getDate("expiry_date") != null ? rs.getDate("expiry_date") : "").append("\"")
                        .append("}");
                }
            }
        }
        return json.append("]").toString();
    }

    private boolean isDuplicateInvoice(int supplierId, String invoiceNo) throws Exception {
        String sql = "SELECT 1 FROM purchase_invoices WHERE supplier_id = ? AND lower(invoice_no) = lower(?) AND status <> 'CANCELLED'";
        try (Connection conn = DBConnect.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            ps.setString(2, invoiceNo.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String autoMatchItem(int supplierId, String itemCode) throws Exception {
        String sql = "SELECT m.package_id, p.sku, pv.variant_name FROM supplier_item_mappings m " +
                     "JOIN packages p ON p.package_id = m.package_id " +
                     "JOIN product_variants pv ON pv.variant_id = p.variant_id " +
                     "WHERE m.supplier_id = ? AND lower(m.supplier_item_code) = lower(?)";
        try (Connection conn = DBConnect.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            ps.setString(2, itemCode.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return String.format("{\"matched\":true,\"packageId\":%d,\"sku\":\"%s\",\"variantName\":\"%s\"}",
                        rs.getInt("package_id"), esc(rs.getString("sku")), esc(rs.getString("variant_name")));
                }
            }
        }
        return "{\"matched\":false}";
    }

    private int createDraftVariantAndPackage(String itemName, int weightG) throws Exception {
        try (Connection conn = DBConnect.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Lấy product_id mặc định đầu tiên và grind_size_id mặc định đầu tiên
                int productId = 1, grindSizeId = 1;
                try (Statement st = conn.createStatement()) {
                    ResultSet rs1 = st.executeQuery("SELECT product_id FROM products LIMIT 1");
                    if (rs1.next()) productId = rs1.getInt(1);
                    ResultSet rs2 = st.executeQuery("SELECT grind_size_id FROM grind_sizes ORDER BY sort_order LIMIT 1");
                    if (rs2.next()) grindSizeId = rs2.getInt(1);
                }
                String suffix = String.valueOf(System.currentTimeMillis() % 100000);
                String varCode = "VAR_DRAFT_" + suffix;
                int variantId;
                String sqlVar = "INSERT INTO product_variants (product_id, variant_code, variant_name, status) " +
                                "VALUES (?, ?, ?, 'DRAFT') RETURNING variant_id";
                try (PreparedStatement ps = conn.prepareStatement(sqlVar)) {
                    ps.setInt(1, productId);
                    ps.setString(2, varCode);
                    ps.setString(3, itemName.trim());
                    ResultSet rs = ps.executeQuery();
                    rs.next();
                    variantId = rs.getInt(1);
                }
                String sku = "SKU_DRAFT_" + suffix;
                String sqlPkg = "INSERT INTO packages (variant_id, grind_size_id, sku, weight_g, status) " +
                                "VALUES (?, ?, ?, ?, 'ACTIVE') RETURNING package_id";
                try (PreparedStatement ps = conn.prepareStatement(sqlPkg)) {
                    ps.setInt(1, variantId);
                    ps.setInt(2, grindSizeId);
                    ps.setString(3, sku);
                    ps.setInt(4, weightG > 0 ? weightG : 250);
                    ResultSet rs = ps.executeQuery();
                    rs.next();
                    int pkgId = rs.getInt(1);
                    conn.commit();
                    return pkgId;
                }
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    private long createInvoice(int supplierId, String invoiceNo, String lookupCode, Date invoiceDate, String inputMethod, int userId) throws Exception {
        String sql = "INSERT INTO purchase_invoices (supplier_id, invoice_no, lookup_code, invoice_date, input_method, status, created_by) " +
                     "VALUES (?, ?, ?, ?, ?, 'DRAFT', ?) RETURNING purchase_invoice_id";
        try (Connection conn = DBConnect.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            ps.setString(2, invoiceNo.trim());
            ps.setString(3, (lookupCode != null && !lookupCode.trim().isEmpty()) ? lookupCode.trim() : null);
            ps.setDate(4, invoiceDate);
            ps.setString(5, (inputMethod != null && !inputMethod.isEmpty()) ? inputMethod : "MANUAL");
            ps.setInt(6, userId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }

    private void addLineAndLot(long invoiceId, int packageId, String supCode, String supName, int qty, long unitCost,
                               String lotCode, Date roastDate, Date expiryDate) throws Exception {
        try (Connection conn = DBConnect.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int nextLineNo = 1;
                try (PreparedStatement psMax = conn.prepareStatement(
                        "SELECT COALESCE(MAX(line_no), 0) + 1 FROM purchase_invoice_lines WHERE purchase_invoice_id = ?")) {
                    psMax.setLong(1, invoiceId);
                    ResultSet rs = psMax.executeQuery();
                    if (rs.next()) nextLineNo = rs.getInt(1);
                }
                long lineId;
                String sqlLine = "INSERT INTO purchase_invoice_lines (purchase_invoice_id, line_no, package_id, supplier_item_code, supplier_item_name, quantity, unit_cost) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING purchase_line_id";
                try (PreparedStatement ps = conn.prepareStatement(sqlLine)) {
                    ps.setLong(1, invoiceId);
                    ps.setInt(2, nextLineNo);
                    ps.setInt(3, packageId);
                    ps.setString(4, (supCode != null && !supCode.trim().isEmpty()) ? supCode.trim() : null);
                    ps.setString(5, (supName != null && !supName.trim().isEmpty()) ? supName.trim() : null);
                    ps.setInt(6, qty);
                    ps.setLong(7, unitCost);
                    ResultSet rs = ps.executeQuery();
                    rs.next();
                    lineId = rs.getLong(1);
                }
                String autoLot = (lotCode != null && !lotCode.trim().isEmpty())
                        ? lotCode.trim().toUpperCase() : "LOT_" + System.currentTimeMillis();
                String sqlLot = "INSERT INTO lots (purchase_line_id, lot_code, roast_date, expiry_date) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sqlLot)) {
                    ps.setLong(1, lineId);
                    ps.setString(2, autoLot);
                    if (roastDate != null) ps.setDate(3, roastDate); else ps.setNull(3, Types.DATE);
                    ps.setDate(4, expiryDate);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    private void updateLineAndLot(long lineId, int qty, long unitCost, Date roastDate, Date expiryDate) throws Exception {
        try (Connection conn = DBConnect.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE purchase_invoice_lines SET quantity = ?, unit_cost = ? WHERE purchase_line_id = ?")) {
                    ps.setInt(1, qty);
                    ps.setLong(2, unitCost);
                    ps.setLong(3, lineId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE lots SET roast_date = ?, expiry_date = ? WHERE purchase_line_id = ?")) {
                    if (roastDate != null) ps.setDate(1, roastDate); else ps.setNull(1, Types.DATE);
                    ps.setDate(2, expiryDate);
                    ps.setLong(3, lineId);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    private void deleteLineAndLot(long lineId) throws Exception {
        try (Connection conn = DBConnect.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM lots WHERE purchase_line_id = ?")) {
                    ps.setLong(1, lineId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM purchase_invoice_lines WHERE purchase_line_id = ?")) {
                    ps.setLong(1, lineId);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    private void confirmInvoice(long invoiceId, int userId) throws Exception {
        try (Connection conn = DBConnect.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE purchase_invoices SET status = 'CONFIRMED', confirmed_at = now() WHERE purchase_invoice_id = ? AND status = 'DRAFT'")) {
                    ps.setLong(1, invoiceId);
                    if (ps.executeUpdate() == 0) throw new Exception("Phiếu nhập không ở trạng thái DRAFT!");
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO stock_movements (lot_id, movement_type, quantity_change, performed_by) " +
                        "SELECT l.lot_id, 'IMPORT', pil.quantity, ? FROM purchase_invoice_lines pil " +
                        "JOIN lots l ON l.purchase_line_id = pil.purchase_line_id WHERE pil.purchase_invoice_id = ?")) {
                    ps.setInt(1, userId);
                    ps.setLong(2, invoiceId);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    private void cancelInvoice(long invoiceId) throws Exception {
        try (Connection conn = DBConnect.getConnection(); PreparedStatement ps = conn.prepareStatement(
                "UPDATE purchase_invoices SET status = 'CANCELLED' WHERE purchase_invoice_id = ? AND status = 'DRAFT'")) {
            ps.setLong(1, invoiceId);
            if (ps.executeUpdate() == 0) throw new Exception("Chỉ hủy được phiếu đang DRAFT!");
        }
    }

    private String listLots() throws Exception {
        String sql = "SELECT s.lot_id, s.lot_code, p.sku, pv.variant_name, s.roast_date, s.expiry_date, s.quantity_on_hand " +
                     "FROM v_lot_stock s JOIN packages p ON p.package_id = s.package_id " +
                     "JOIN product_variants pv ON pv.variant_id = p.variant_id ORDER BY s.expiry_date ASC";
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DBConnect.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append("{")
                    .append("\"lotId\":").append(rs.getLong("lot_id")).append(",")
                    .append("\"lotCode\":\"").append(esc(rs.getString("lot_code"))).append("\",")
                    .append("\"sku\":\"").append(esc(rs.getString("sku"))).append("\",")
                    .append("\"variantName\":\"").append(esc(rs.getString("variant_name"))).append("\",")
                    .append("\"roastDate\":\"").append(rs.getDate("roast_date") != null ? rs.getDate("roast_date") : "").append("\",")
                    .append("\"expiryDate\":\"").append(rs.getDate("expiry_date")).append("\",")
                    .append("\"qty\":").append(rs.getInt("quantity_on_hand"))
                    .append("}");
            }
        }
        return json.append("]").toString();
    }

    private void updateLotRoastDate(long lotId, Date roastDate) throws Exception {
        try (Connection conn = DBConnect.getConnection(); PreparedStatement ps = conn.prepareStatement(
                "UPDATE lots SET roast_date = ? WHERE lot_id = ?")) {
            if (roastDate != null) ps.setDate(1, roastDate); else ps.setNull(1, Types.DATE);
            ps.setLong(2, lotId);
            ps.executeUpdate();
        }
    }

    private void updateLotExpiryDate(long lotId, Date expiryDate) throws Exception {
        try (Connection conn = DBConnect.getConnection(); PreparedStatement ps = conn.prepareStatement(
                "UPDATE lots SET expiry_date = ? WHERE lot_id = ?")) {
            ps.setDate(1, expiryDate);
            ps.setLong(2, lotId);
            ps.executeUpdate();
        }
    }

    private String listMappings() throws Exception {
        String sql = "SELECT m.mapping_id, m.supplier_id, s.supplier_name, m.supplier_item_code, m.package_id, p.sku, pv.variant_name " +
                     "FROM supplier_item_mappings m JOIN suppliers s ON s.supplier_id = m.supplier_id " +
                     "JOIN packages p ON p.package_id = m.package_id JOIN product_variants pv ON pv.variant_id = p.variant_id " +
                     "ORDER BY m.mapping_id DESC";
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DBConnect.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append("{")
                    .append("\"mappingId\":").append(rs.getInt("mapping_id")).append(",")
                    .append("\"supplierId\":").append(rs.getInt("supplier_id")).append(",")
                    .append("\"supplierName\":\"").append(esc(rs.getString("supplier_name"))).append("\",")
                    .append("\"supplierItemCode\":\"").append(esc(rs.getString("supplier_item_code"))).append("\",")
                    .append("\"packageId\":").append(rs.getInt("package_id")).append(",")
                    .append("\"sku\":\"").append(esc(rs.getString("sku"))).append("\",")
                    .append("\"variantName\":\"").append(esc(rs.getString("variant_name"))).append("\"")
                    .append("}");
            }
        }
        return json.append("]").toString();
    }

    private void saveMapping(String mappingIdStr, int supplierId, String code, int packageId) throws Exception {
        try (Connection conn = DBConnect.getConnection()) {
            if (mappingIdStr != null && !mappingIdStr.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE supplier_item_mappings SET supplier_id=?, supplier_item_code=?, package_id=? WHERE mapping_id=?")) {
                    ps.setInt(1, supplierId);
                    ps.setString(2, code.trim());
                    ps.setInt(3, packageId);
                    ps.setInt(4, Integer.parseInt(mappingIdStr));
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO supplier_item_mappings (supplier_id, supplier_item_code, package_id) VALUES (?, ?, ?) " +
                        "ON CONFLICT (supplier_id, supplier_item_code) DO UPDATE SET package_id = EXCLUDED.package_id")) {
                    ps.setInt(1, supplierId);
                    ps.setString(2, code.trim());
                    ps.setInt(3, packageId);
                    ps.executeUpdate();
                }
            }
        }
    }

    private void deleteMapping(int mappingId) throws Exception {
        try (Connection conn = DBConnect.getConnection(); PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM supplier_item_mappings WHERE mapping_id = ?")) {
            ps.setInt(1, mappingId);
            ps.executeUpdate();
        }
    }

    private String getFormLookups() throws Exception {
        StringBuilder sup = new StringBuilder("[");
        StringBuilder pkg = new StringBuilder("[");
        try (Connection conn = DBConnect.getConnection(); Statement st = conn.createStatement()) {
            ResultSet rs1 = st.executeQuery("SELECT supplier_id, supplier_code, supplier_name FROM suppliers WHERE status = 'ACTIVE' ORDER BY supplier_name");
            boolean f1 = true;
            while (rs1.next()) {
                if (!f1) sup.append(","); f1 = false;
                sup.append("{\"id\":").append(rs1.getInt(1)).append(",\"name\":\"").append(esc(rs1.getString(2) + " - " + rs1.getString(3))).append("\"}");
            }
            ResultSet rs2 = st.executeQuery("SELECT p.package_id, p.sku, pv.variant_name, p.weight_g FROM packages p JOIN product_variants pv ON pv.variant_id = p.variant_id ORDER BY p.sku");
            boolean f2 = true;
            while (rs2.next()) {
                if (!f2) pkg.append(","); f2 = false;
                pkg.append("{\"id\":").append(rs2.getInt(1)).append(",\"label\":\"").append(esc(rs2.getString(2) + " - " + rs2.getString(3) + " (" + rs2.getInt(4) + "g)")).append("\"}");
            }
        }
        return "{\"suppliers\":" + sup.append("]") + ",\"packages\":" + pkg.append("]") + "}";
    }

    private Date parseDate(String s) {
        return (s != null && !s.trim().isEmpty()) ? Date.valueOf(s.trim()) : null;
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}