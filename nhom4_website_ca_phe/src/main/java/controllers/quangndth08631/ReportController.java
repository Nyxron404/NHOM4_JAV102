package controllers.quangndth08631;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import uri.DBConnect;

@WebServlet(name = "ReportController", urlPatterns = {"/api/reports/*"})
public class ReportController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();
        String from = req.getParameter("from");
        String to = req.getParameter("to");

        try (PrintWriter out = resp.getWriter()) {
            switch (path != null ? path : "") {
                case "/summary":
                    out.print(getSummary(from, to)); break;
                case "/revenue-day":
                    out.print(queryTwoCols(
                        "SELECT to_char(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM-DD') AS label, COALESCE(SUM(total_amount),0) AS val " +
                        "FROM v_sales_invoice_totals WHERE status = 'PAID' " + dateFilter("created_at", from, to) +
                        " GROUP BY 1 ORDER BY 1 DESC", from, to));
                    break;
                case "/revenue-month":
                    out.print(queryTwoCols(
                        "SELECT to_char(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM') AS label, COALESCE(SUM(total_amount),0) AS val " +
                        "FROM v_sales_invoice_totals WHERE status = 'PAID' GROUP BY 1 ORDER BY 1 DESC", null, null));
                    break;
                case "/revenue-payment":
                    out.print(queryTwoCols(
                        "SELECT pm.method_name AS label, COALESCE(SUM(p.amount),0) AS val " +
                        "FROM payments p JOIN payment_methods pm ON pm.payment_method_id = p.payment_method_id " +
                        "WHERE p.status = 'SUCCESS' " + dateFilter("p.paid_at", from, to) +
                        " GROUP BY 1 ORDER BY 2 DESC", from, to));
                    break;
                case "/expenses":
                    out.print(queryTwoCols(
                        "SELECT pi.invoice_no || ' (' || s.supplier_name || ' - ' || pi.invoice_date || ')' AS label, COALESCE(v.total_amount,0) AS val " +
                        "FROM purchase_invoices pi JOIN suppliers s ON s.supplier_id = pi.supplier_id " +
                        "LEFT JOIN v_purchase_invoice_totals v ON v.purchase_invoice_id = pi.purchase_invoice_id " +
                        "WHERE pi.status = 'CONFIRMED' " + dateFilterDate("pi.invoice_date", from, to) +
                        " ORDER BY pi.invoice_date DESC", from, to));
                    break;
                case "/best-selling":
                    out.print(queryThreeCols(
                        "SELECT p.sku, pv.variant_name, COALESCE(SUM(sil.quantity),0) AS qty, COALESCE(SUM(sil.quantity * sil.unit_price),0) AS rev " +
                        "FROM sales_invoice_lines sil JOIN sales_invoices si ON si.sales_invoice_id = sil.sales_invoice_id " +
                        "JOIN packages p ON p.package_id = sil.package_id JOIN product_variants pv ON pv.variant_id = p.variant_id " +
                        "WHERE si.status = 'PAID' " + dateFilter("si.created_at", from, to) +
                        " GROUP BY p.sku, pv.variant_name ORDER BY qty DESC LIMIT 20", from, to));
                    break;
                case "/by-region":
                    out.print(queryTwoCols(
                        "SELECT gr.region_name AS label, COALESCE(SUM(sil.quantity * sil.unit_price),0) AS val " +
                        "FROM sales_invoice_lines sil JOIN sales_invoices si ON si.sales_invoice_id = sil.sales_invoice_id " +
                        "JOIN packages p ON p.package_id = sil.package_id JOIN product_variants pv ON pv.variant_id = p.variant_id " +
                        "JOIN growing_regions gr ON gr.region_id = pv.region_id WHERE si.status = 'PAID' " + dateFilter("si.created_at", from, to) +
                        " GROUP BY 1 ORDER BY 2 DESC", from, to));
                    break;
                case "/by-variety":
                    out.print(queryTwoCols(
                        "SELECT cv.variety_name AS label, COALESCE(SUM(sil.quantity * sil.unit_price),0) AS val " +
                        "FROM sales_invoice_lines sil JOIN sales_invoices si ON si.sales_invoice_id = sil.sales_invoice_id " +
                        "JOIN packages p ON p.package_id = sil.package_id JOIN product_variants pv ON pv.variant_id = p.variant_id " +
                        "JOIN coffee_varieties cv ON cv.variety_id = pv.variety_id WHERE si.status = 'PAID' " + dateFilter("si.created_at", from, to) +
                        " GROUP BY 1 ORDER BY 2 DESC", from, to));
                    break;
                case "/by-process":
                    out.print(queryTwoCols(
                        "SELECT pm.process_name AS label, COALESCE(SUM(sil.quantity * sil.unit_price),0) AS val " +
                        "FROM sales_invoice_lines sil JOIN sales_invoices si ON si.sales_invoice_id = sil.sales_invoice_id " +
                        "JOIN packages p ON p.package_id = sil.package_id JOIN product_variants pv ON pv.variant_id = p.variant_id " +
                        "JOIN processing_methods pm ON pm.process_id = pv.process_id WHERE si.status = 'PAID' " + dateFilter("si.created_at", from, to) +
                        " GROUP BY 1 ORDER BY 2 DESC", from, to));
                    break;
                case "/by-roast":
                    out.print(queryTwoCols(
                        "SELECT rl.roast_name AS label, COALESCE(SUM(sil.quantity * sil.unit_price),0) AS val " +
                        "FROM sales_invoice_lines sil JOIN sales_invoices si ON si.sales_invoice_id = sil.sales_invoice_id " +
                        "JOIN packages p ON p.package_id = sil.package_id JOIN product_variants pv ON pv.variant_id = p.variant_id " +
                        "JOIN roast_levels rl ON rl.roast_level_id = pv.roast_level_id WHERE si.status = 'PAID' " + dateFilter("si.created_at", from, to) +
                        " GROUP BY 1 ORDER BY 2 DESC", from, to));
                    break;
                case "/inventory":
                    out.print(getInventory()); break;
                case "/near-expiry":
                    out.print(getNearExpiry()); break;
                default:
                    resp.setStatus(404);
                    out.print("{\"error\":\"Báo cáo không tồn tại\"}");
            }
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().print("{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private String getSummary(String from, String to) throws Exception {
        long incomeToday = 0, revenuePeriod = 0, expensePeriod = 0, disposalLoss = 0;
        int nearExpiryCount = 0;
        try (Connection conn = DBConnect.getConnection()) {
            // Thu trong ngày
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(total_amount),0) FROM v_sales_invoice_totals WHERE status = 'PAID' " +
                    "AND (created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date = (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date");
                 ResultSet rs = ps.executeQuery()) { if (rs.next()) incomeToday = rs.getLong(1); }

            // Tổng thu theo khoảng thời gian
            String sqlRev = "SELECT COALESCE(SUM(total_amount),0) FROM v_sales_invoice_totals WHERE status = 'PAID' " + dateFilter("created_at", from, to);
            try (PreparedStatement ps = conn.prepareStatement(sqlRev)) {
                bindDates(ps, from, to);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) revenuePeriod = rs.getLong(1); }
            }

            // Tổng chi nhập hàng theo khoảng thời gian
            String sqlExp = "SELECT COALESCE(SUM(v.total_amount),0) FROM purchase_invoices pi " +
                            "JOIN v_purchase_invoice_totals v ON v.purchase_invoice_id = pi.purchase_invoice_id " +
                            "WHERE pi.status = 'CONFIRMED' " + dateFilterDate("pi.invoice_date", from, to);
            try (PreparedStatement ps = conn.prepareStatement(sqlExp)) {
                bindDates(ps, from, to);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) expensePeriod = rs.getLong(1); }
            }

            // Lỗ xuất hủy
            String sqlLoss = "SELECT COALESCE(SUM(loss_amount),0) FROM v_disposal_losses WHERE 1=1 " + dateFilter("disposed_at", from, to);
            try (PreparedStatement ps = conn.prepareStatement(sqlLoss)) {
                bindDates(ps, from, to);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) disposalLoss = rs.getLong(1); }
            }

            // Lô cận hạn
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM v_lot_stock WHERE quantity_on_hand > 0 AND expiry_date <= CURRENT_DATE + " +
                    "(SELECT setting_value::int FROM system_settings WHERE setting_key = 'inventory.near_expiry_days')");
                 ResultSet rs = ps.executeQuery()) { if (rs.next()) nearExpiryCount = rs.getInt(1); }
        }
        long netCashflow = revenuePeriod - expensePeriod;
        long profit = revenuePeriod - expensePeriod - disposalLoss;
        return String.format(
            "{\"incomeToday\":%d,\"revenuePeriod\":%d,\"expensePeriod\":%d,\"disposalLoss\":%d,\"netCashflow\":%d,\"profit\":%d,\"nearExpiryCount\":%d}",
            incomeToday, revenuePeriod, expensePeriod, disposalLoss, netCashflow, profit, nearExpiryCount
        );
    }

    private String getInventory() throws Exception {
        String sql = "SELECT ps.sku, pv.variant_name, ps.quantity_on_hand, ps.min_stock_level, ps.is_low_stock " +
                     "FROM v_package_stock ps JOIN packages p ON p.package_id = ps.package_id " +
                     "JOIN product_variants pv ON pv.variant_id = p.variant_id ORDER BY ps.quantity_on_hand ASC";
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DBConnect.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(","); first = false;
                json.append("{\"sku\":\"").append(esc(rs.getString(1)))
                    .append("\",\"variantName\":\"").append(esc(rs.getString(2)))
                    .append("\",\"qty\":").append(rs.getInt(3))
                    .append(",\"minStock\":").append(rs.getInt(4))
                    .append(",\"lowStock\":").append(rs.getBoolean(5)).append("}");
            }
        }
        return json.append("]").toString();
    }

    private String getNearExpiry() throws Exception {
        String sql = "SELECT s.lot_code, p.sku, pv.variant_name, s.expiry_date, s.quantity_on_hand " +
                     "FROM v_lot_stock s JOIN packages p ON p.package_id = s.package_id " +
                     "JOIN product_variants pv ON pv.variant_id = p.variant_id " +
                     "WHERE s.quantity_on_hand > 0 AND s.expiry_date <= CURRENT_DATE + " +
                     "(SELECT setting_value::int FROM system_settings WHERE setting_key = 'inventory.near_expiry_days') ORDER BY s.expiry_date ASC";
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DBConnect.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(","); first = false;
                json.append("{\"lotCode\":\"").append(esc(rs.getString(1)))
                    .append("\",\"sku\":\"").append(esc(rs.getString(2)))
                    .append("\",\"variantName\":\"").append(esc(rs.getString(3)))
                    .append("\",\"expiryDate\":\"").append(rs.getDate(4))
                    .append("\",\"qty\":").append(rs.getInt(5)).append("}");
            }
        }
        return json.append("]").toString();
    }

    private String queryTwoCols(String sql, String from, String to) throws Exception {
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DBConnect.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDates(ps, from, to);
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(","); first = false;
                    json.append("{\"label\":\"").append(esc(rs.getString(1))).append("\",\"value\":").append(rs.getLong(2)).append("}");
                }
            }
        }
        return json.append("]").toString();
    }

    private String queryThreeCols(String sql, String from, String to) throws Exception {
        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DBConnect.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDates(ps, from, to);
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(","); first = false;
                    json.append("{\"sku\":\"").append(esc(rs.getString(1)))
                        .append("\",\"name\":\"").append(esc(rs.getString(2)))
                        .append("\",\"qty\":").append(rs.getInt(3))
                        .append(",\"revenue\":").append(rs.getLong(4)).append("}");
                }
            }
        }
        return json.append("]").toString();
    }

    private String dateFilter(String col, String from, String to) {
        String s = "";
        if (from != null && !from.isEmpty()) s += " AND (" + col + " AT TIME ZONE 'Asia/Ho_Chi_Minh')::date >= ? ";
        if (to != null && !to.isEmpty()) s += " AND (" + col + " AT TIME ZONE 'Asia/Ho_Chi_Minh')::date <= ? ";
        return s;
    }

    private String dateFilterDate(String col, String from, String to) {
        String s = "";
        if (from != null && !from.isEmpty()) s += " AND " + col + " >= ? ";
        if (to != null && !to.isEmpty()) s += " AND " + col + " <= ? ";
        return s;
    }

    private void bindDates(PreparedStatement ps, String from, String to) throws SQLException {
        int idx = 1;
        if (from != null && !from.isEmpty()) ps.setDate(idx++, Date.valueOf(from));
        if (to != null && !to.isEmpty()) ps.setDate(idx++, Date.valueOf(to));
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}