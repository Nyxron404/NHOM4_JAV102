package controllers.longdtta00178;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import controllers.minhpvth08530.AuthSession;
import dao.longdtta00178.SalesConfigDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * API trang cấu hình bán hàng (longdtta001784). Chỉ tài khoản có quyền SALES_CONFIG (chủ cửa hàng).
 *
 *   GET  /api/cau-hinh-ban-hang                       -> {"settings":[...],"methods":[...],"exceptions":[...]}
 *   POST action=saveSettings  store_name, address, phone, tax_code, vat_rate, footer_note   [159]
 *   POST action=toggleMethod  id, active=true|false                                       [163]
 *   POST action=closePayment  paymentId                                                   [162.2]
 *   POST action=acceptPayment paymentId                                                   [162.3]
 * Mọi POST trả lại toàn bộ dữ liệu trang + "message".
 */
@WebServlet("/api/cau-hinh-ban-hang")
public class SalesConfigController extends HttpServlet {

    private static final String PERM_CONFIG = "SALES_CONFIG";
    private final SalesConfigDAO dao = new SalesConfigDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        if (!allowed(req, resp)) return;
        try {
            writeAll(resp, null);
        } catch (Exception e) {
            e.printStackTrace();
            fail(resp, 500, "Lỗi tải cấu hình: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");
        if (!allowed(req, resp)) return;
        int userId = AuthSession.userId(req);
        try {
            String action = req.getParameter("action");
            String message;
            switch (action == null ? "" : action) {
                case "saveSettings" -> {
                    Map<String, String> values = new LinkedHashMap<>();
                    for (String key : SalesConfigDAO.INVOICE_KEYS.keySet()) {
                        values.put(key, req.getParameter(key));
                    }
                    int changed = dao.saveInvoiceSettings(values, userId);
                    message = changed == 0 ? "Không có thay đổi" : "Đã lưu " + changed + " mục";
                }
                case "toggleMethod" -> {
                    boolean active = "true".equals(req.getParameter("active"));
                    dao.setPaymentMethodActive(Integer.parseInt(req.getParameter("id")), active, userId);
                    message = active ? "Đã bật phương thức" : "Đã tắt phương thức";
                }
                case "closePayment" -> {
                    dao.closePayment(Long.parseLong(req.getParameter("paymentId")), userId);
                    message = "Đã đóng giao dịch";
                }
                case "acceptPayment" -> {
                    dao.acceptPaidPayment(Long.parseLong(req.getParameter("paymentId")), userId);
                    message = "Đã ghi nhận tiền. Mở đơn ở màn bán hàng để xác nhận.";
                }
                default -> {
                    fail(resp, 400, "Thao tác không hợp lệ: " + action);
                    return;
                }
            }
            writeAll(resp, message);
        } catch (NumberFormatException e) {
            fail(resp, 400, "Tham số không hợp lệ");
        } catch (IllegalArgumentException e) {
            fail(resp, 400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            fail(resp, 500, "Lỗi lưu cấu hình: " + e.getMessage());
        }
    }

    private void writeAll(HttpServletResponse resp, String message) throws Exception {
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("message", message);
        String json = SalesJson.object(head);                       // {"message":"..."}
        resp.getWriter().write(json.substring(0, json.length() - 1)
                + ",\"settings\":" + array(dao.findInvoiceSettings())
                + ",\"methods\":" + array(dao.findPaymentMethods())
                + ",\"exceptions\":" + array(dao.findPaymentExceptions())
                + "}");
    }

    /** Chưa đăng nhập -> 401, không có quyền -> 403 */
    private static boolean allowed(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (AuthSession.userId(req) == null) {
            fail(resp, 401, "Phiên đăng nhập đã hết, đăng nhập lại");
            return false;
        }
        if (!AuthSession.can(AuthSession.current(req), PERM_CONFIG)) {
            fail(resp, 403, "Chỉ chủ cửa hàng được vào trang cấu hình bán hàng");
            return false;
        }
        return true;
    }

    private static String array(List<Map<String, Object>> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(SalesJson.object(list.get(i)));
        }
        return sb.append(']').toString();
    }

    private static void fail(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.getWriter().write(SalesJson.error(message));
    }
}