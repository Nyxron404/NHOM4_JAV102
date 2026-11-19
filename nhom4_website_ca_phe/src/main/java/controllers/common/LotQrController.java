package controllers.common;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import controllers.minhpvth08530.AuthSession;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import services.LotQrService;
import uri.DBConnect;

/**
 * Ảnh QR của lô (dùng chung cho trang Kho và Nhập kho).
 *
 *   GET  /api/lot-qr?lotId=5               -> ảnh PNG: QR mã lô + mã lô, SKU, HSD bên dưới (để xem / in tem)
 *   GET  /api/lot-qr?lotId=5&download=1    -> như trên, tải về file QR_<mã lô>.png
 *   POST /api/lot-qr  action=generateMissing [purchaseInvoiceId]
 *                                          -> tạo + upload Cloudinary cho các lô chưa có QR (quyền INVENTORY_MANAGE)
 *                                             trả {"created":n,"cloud":true|false}
 */
@WebServlet("/api/lot-qr")
public class LotQrController extends HttpServlet {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long lotId;
        try {
            lotId = Long.parseLong(req.getParameter("lotId"));
        } catch (NumberFormatException e) {
            resp.sendError(400, "Thiếu mã lô");
            return;
        }
        String lotCode, sku;
        LocalDate expiry;
        try (Connection con = DBConnect.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                     SELECT lo.lot_code, lo.expiry_date, pk.sku
                     FROM lots lo
                     JOIN purchase_invoice_lines pil ON pil.purchase_line_id = lo.purchase_line_id
                     JOIN packages pk ON pk.package_id = pil.package_id
                     WHERE lo.lot_id = ?
                     """)) {
            ps.setLong(1, lotId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    resp.sendError(404, "Không tìm thấy lô");
                    return;
                }
                lotCode = rs.getString("lot_code");
                sku = rs.getString("sku");
                expiry = rs.getObject("expiry_date", LocalDate.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
            resp.sendError(500, "Lỗi đọc lô");
            return;
        }

        byte[] png = LotQrService.png(lotCode, 480, lotCode, sku, "HSD " + expiry.format(DMY));
        resp.setContentType("image/png");
        resp.setHeader("Cache-Control", "no-cache");
        if ("1".equals(req.getParameter("download"))) {
            resp.setHeader("Content-Disposition",
                    "attachment; filename=\"QR_" + lotCode.replaceAll("[^A-Za-z0-9_-]", "_") + ".png\"");
        }
        resp.getOutputStream().write(png);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        if (!AuthSession.can(AuthSession.current(req), "INVENTORY_MANAGE")) {
            resp.setStatus(403);
            resp.getWriter().write("{\"error\":\"Chỉ chủ cửa hàng được tạo mã QR\"}");
            return;
        }
        if (!"generateMissing".equals(req.getParameter("action"))) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Thao tác không hợp lệ\"}");
            return;
        }
        try {
            String invoice = req.getParameter("purchaseInvoiceId");
            int created = LotQrService.ensureForPurchaseInvoice(
                    invoice == null || invoice.isBlank() ? null : Long.valueOf(invoice));
            resp.getWriter().write("{\"created\":" + created + ",\"cloud\":" + LotQrService.cloudEnabled() + "}");
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Lỗi tạo QR: " + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}");
        }
    }
}