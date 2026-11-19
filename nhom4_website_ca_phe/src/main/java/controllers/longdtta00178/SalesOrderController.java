package controllers.longdtta00178;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;

import dao.longdtta00178.SalesOrderDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import models.longdtta00178.SalesInvoice;

/**
 * API đơn đang lập ở màn bán hàng.
 *
 *   GET  /api/ban-hang/don?id=5                          -> xem đơn
 *   POST /api/ban-hang/don  action=create                -> [130] tạo đơn mới
 *   POST /api/ban-hang/don  action=addPackage  invoiceId, packageId, quantity(mặc định 1)   -> [132]
 *   POST /api/ban-hang/don  action=updateQty   invoiceId, lineId, quantity                  -> [132.2]
 *   POST /api/ban-hang/don  action=removeLine  invoiceId, lineId                            -> [134]
 *   POST /api/ban-hang/don  action=confirm     invoiceId, cashGiven (tiền khách đưa)        -> [138 + 140 + 142]
 *   POST /api/ban-hang/don  action=cancel      invoiceId, reason                            -> [135]
 *   POST /api/ban-hang/don  action=printLog    invoiceId                                    -> [145] ghi lần xuất hóa đơn
 *   GET  /api/ban-hang/don?action=overview                -> chỉ số trong ngày + thông tin in hóa đơn
 *
 * Mọi lệnh thành công đều trả lại toàn bộ đơn: {"invoice":{...},"lines":[...]} để JS vẽ lại.
 * Lỗi: {"error":"..."} với mã 400 (lỗi nghiệp vụ / tham số), 404 (không có đơn), 500 (lỗi DB).
 */
@WebServlet("/api/ban-hang/don")
public class SalesOrderController extends HttpServlet {

    private final SalesOrderDAO dao = new SalesOrderDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        try {
            if ("overview".equals(req.getParameter("action"))) {
                resp.getWriter().write(SalesJson.object(dao.findOverview()));
                return;
            }
            long invoiceId = Long.parseLong(req.getParameter("id"));
            writeOrder(resp, invoiceId);
        } catch (NumberFormatException e) {
            fail(resp, 400, "Thiếu hoặc sai mã đơn");
        } catch (Exception e) {
            e.printStackTrace();
            fail(resp, 500, "Lỗi tải đơn: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");
        try {
            String action = req.getParameter("action");
            long invoiceId;

            if ("create".equals(action)) {
                invoiceId = dao.createInvoice(currentUserId(req));
            } else {
                invoiceId = Long.parseLong(req.getParameter("invoiceId"));
                if ("addPackage".equals(action)) {
                    String qty = req.getParameter("quantity");
                    dao.addPackage(invoiceId,
                            Integer.parseInt(req.getParameter("packageId")),
                            qty == null || qty.isBlank() ? 1 : Integer.parseInt(qty));
                } else if ("updateQty".equals(action)) {
                    dao.updateQuantity(invoiceId,
                            Long.parseLong(req.getParameter("lineId")),
                            Integer.parseInt(req.getParameter("quantity")));
                } else if ("removeLine".equals(action)) {
                    dao.removeLine(invoiceId, Long.parseLong(req.getParameter("lineId")));
                } else if ("confirm".equals(action)) {
                    dao.confirmOrder(invoiceId, currentUserId(req), toMoney(req.getParameter("cashGiven")));
                } else if ("cancel".equals(action)) {
                    dao.cancelPendingOrder(invoiceId, currentUserId(req), req.getParameter("reason"));
                } else if ("printLog".equals(action)) {
                    dao.logExportInvoice(invoiceId, currentUserId(req));
                } else {
                    fail(resp, 400, "Thao tác không hợp lệ: " + action);
                    return;
                }
            }
            writeOrder(resp, invoiceId);

        } catch (NumberFormatException e) {
            fail(resp, 400, "Tham số không hợp lệ");
        } catch (IllegalArgumentException e) {        // lỗi nghiệp vụ: vượt tồn, đơn đã đóng...
            fail(resp, 400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            fail(resp, 500, "Lỗi xử lý đơn: " + e.getMessage());
        }
    }

    /** Đọc lại đơn + dòng hàng + thanh toán + lô đã trừ rồi trả JSON */
    private void writeOrder(HttpServletResponse resp, long invoiceId) throws IOException, SQLException {
        SalesInvoice invoice = dao.findInvoice(invoiceId);
        if (invoice == null) {
            fail(resp, 404, "Không tìm thấy đơn");
            return;
        }
        resp.getWriter().write(SalesJson.order(invoice, dao.findLines(invoiceId),
                dao.findPayments(invoiceId), dao.findAllocations(invoiceId)));
    }

    /**
     * Người thao tác: session "userId" (AuthSession.USER_ID của phần đăng nhập).
     * Chưa đăng nhập thì tạm dùng tài khoản quầy STAFF.
     */
    private int currentUserId(HttpServletRequest req) throws SQLException {
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("userId") instanceof Number id) {
            return id.intValue();
        }
        Integer fallback = dao.findDefaultUserId();
        if (fallback == null) throw new IllegalArgumentException("Chưa có tài khoản người dùng nào");
        return fallback;
    }

    /** "500.000" hoặc "500000" -> 500000; trống -> null */
    private static BigDecimal toMoney(String value) {
        if (value == null) return null;
        String digits = value.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : new BigDecimal(digits);
    }

    private static void fail(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.getWriter().write(SalesJson.error(message));
    }
}