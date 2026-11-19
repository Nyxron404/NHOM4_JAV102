package controllers.longdtta00178;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import dao.longdtta00178.VnpayDAO;
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
    private final VnpayDAO vnpayDao = new VnpayDAO();
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        try {
            if ("overview".equals(req.getParameter("action"))) {
                resp.getWriter().write(SalesJson.object(dao.findOverview()));
                return;
            }
                        if ("paymentStatus".equals(req.getParameter("action"))) {
                long paymentId = Long.parseLong(req.getParameter("paymentId"));
                resp.getWriter().write(SalesJson.object(vnpayDao.findPaymentStatus(paymentId)));
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
            } else if ("vnpayCreate".equals(action)) {
                createVnpayPayment(req, resp, Long.parseLong(req.getParameter("invoiceId")));
                return;                                      // trả thông tin QR, không trả đơn
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

        /**
     * [133] Tạo giao dịch VNPay chờ thanh toán + link thanh toán đã ký.
     * Trả: {paymentId, txnRef, amount, invoiceNo, payUrl, expireMinutes, mock}
     */
    private void createVnpayPayment(HttpServletRequest req, HttpServletResponse resp, long invoiceId)
            throws IOException, SQLException {
        Map<String, Object> payment = vnpayDao.createPayment(invoiceId);
        BigDecimal amount = (BigDecimal) payment.get("amount");

        Map<String, String> vnp = new LinkedHashMap<>();
        vnp.put("vnp_Version", "2.1.0");
        vnp.put("vnp_Command", "pay");
        vnp.put("vnp_TmnCode", VnpayConfig.tmnCode());
        vnp.put("vnp_Amount", amount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString());   // x100
        vnp.put("vnp_CurrCode", "VND");
        vnp.put("vnp_TxnRef", (String) payment.get("txnRef"));
        vnp.put("vnp_OrderInfo", "Thanh toan don " + payment.get("invoiceNo"));      // không dấu
        vnp.put("vnp_OrderType", "other");
        vnp.put("vnp_Locale", "vn");
        vnp.put("vnp_ReturnUrl", VnpayConfig.returnUrl(req));
        vnp.put("vnp_IpAddr", VnpayConfig.clientIp(req));
        vnp.put("vnp_CreateDate", VnpayConfig.now());
        vnp.put("vnp_ExpireDate", VnpayConfig.plusMinutes(VnpayConfig.EXPIRE_MINUTES));

        Map<String, Object> result = new LinkedHashMap<>(payment);
        result.put("payUrl", VnpayConfig.payUrl(req) + "?" + VnpayConfig.signedQuery(vnp));
        result.put("expireMinutes", VnpayConfig.EXPIRE_MINUTES);
        result.put("mock", VnpayConfig.isMock());
        resp.getWriter().write(SalesJson.object(result));
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