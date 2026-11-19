package controllers.longdtta00178;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import dao.longdtta00178.SalesEditDAO;
import java.util.ArrayList;
import controllers.minhpvth08530.AuthSession;
import dao.longdtta00178.SalesInvoiceDAO;
import dao.longdtta00178.SalesOrderDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import models.longdtta00178.SalesInvoice;

/**
 * API trang hóa đơn (longdtta001782).
 *
 *   GET  /api/hoa-don?action=list&keyword=&from=&to=&methodId=&status=&page=1   -> [148–152]
 *   GET  /api/hoa-don?action=methods                                            -> ô lọc thanh toán
 *   GET  /api/hoa-don?id=5                                                      -> [153] chi tiết
 *        trả {"order":{invoice,lines,payments,allocations},"extra":{...},"canCancel":true}
 *   POST /api/hoa-don  action=reprint  id                                       -> [147] trả {"reprintCount":n}
 *   POST /api/hoa-don  action=cancel   id, reason, restock(true/false), refund(true/false)
 *                                                                               -> [154 + 156/158 + 157]
 * Hủy đơn đã xác nhận cần quyền SALES_INVOICE_CANCEL (chủ cửa hàng).
 */
@WebServlet("/api/hoa-don")
public class SalesInvoiceController extends HttpServlet {

    private static final String PERM_CANCEL = "SALES_INVOICE_CANCEL";
        private static final String PERM_EDIT = "SALES_INVOICE_EDIT";
    private final SalesInvoiceDAO dao = new SalesInvoiceDAO();
    private final SalesOrderDAO orderDao = new SalesOrderDAO();
        private final SalesEditDAO editDao = new SalesEditDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        try {
            String action = req.getParameter("action");
            if ("list".equals(action)) {
                writeList(req, resp);
            } else if ("methods".equals(action)) {
                resp.getWriter().write(array(dao.findPaymentMethods()));
                            } else if ("lot".equals(action)) {
                // [155] tra mã lô khi sửa đơn (quét / gõ)
                resp.getWriter().write(SalesJson.object(editDao.findLot(req.getParameter("code"))));
            } else {
                writeDetail(req, resp, Long.parseLong(req.getParameter("id")));
            }
        } catch (NumberFormatException | java.time.format.DateTimeParseException e) {
            fail(resp, 400, "Tham số không hợp lệ");
                    } catch (IllegalArgumentException e) {
            fail(resp, 400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            fail(resp, 500, "Lỗi tải hóa đơn: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");
        try {
            String action = req.getParameter("action");
            long invoiceId = Long.parseLong(req.getParameter("id"));
            Integer userId = AuthSession.userId(req);
            if (userId == null) {
                fail(resp, 401, "Phiên đăng nhập đã hết, đăng nhập lại");
                return;
            }

            if ("reprint".equals(action)) {
                int count = dao.logReprint(invoiceId, userId);
                resp.getWriter().write("{\"reprintCount\":" + count + "}");
            } else if ("cancel".equals(action)) {
                if (!canCancel(req)) {
                    fail(resp, 403, "Chỉ chủ cửa hàng được hủy đơn đã xác nhận");
                    return;
                }
                dao.cancelPaidOrder(invoiceId, userId, req.getParameter("reason"),
                        "true".equals(req.getParameter("restock")),
                        "true".equals(req.getParameter("refund")));
                writeDetail(req, resp, invoiceId);
                            } else if ("edit".equals(action)) {
                // [155] lots = "lotId:soTui:soTuiQuetThem;..." (toàn bộ lô của đơn sau khi sửa)
                List<SalesEditDAO.LotQty> target = new ArrayList<>();
                String lots = req.getParameter("lots");
                if (lots != null && !lots.isBlank()) {
                    for (String item : lots.split(";")) {
                        String[] f = item.split(":");
                        target.add(new SalesEditDAO.LotQty(Long.parseLong(f[0]), Integer.parseInt(f[1]),
                                f.length > 2 ? Integer.parseInt(f[2]) : 0));
                    }
                }
                String cash = req.getParameter("cashGiven");
                String digits = cash == null ? "" : cash.replaceAll("[^0-9]", "");
                editDao.saveEdit(invoiceId, userId, isOwnerEditor(req), target,
                        digits.isEmpty() ? null : new java.math.BigDecimal(digits), req.getParameter("reason"));
                writeDetail(req, resp, invoiceId);
            } else {
                fail(resp, 400, "Thao tác không hợp lệ: " + action);
            }
        } catch (NumberFormatException e) {
            fail(resp, 400, "Tham số không hợp lệ");
        } catch (IllegalArgumentException e) {      // lỗi nghiệp vụ: đơn không ở trạng thái đã xác nhận, thiếu lý do...
            fail(resp, 400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            fail(resp, 500, "Lỗi xử lý hóa đơn: " + e.getMessage());
        }
    }

    /* [148–152] */
    private void writeList(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String page = req.getParameter("page");
        String methodId = blankToNull(req.getParameter("methodId"));
        Map<String, Object> result = dao.search(
                blankToNull(req.getParameter("keyword")),
                date(req.getParameter("from")),
                date(req.getParameter("to")),
                methodId == null ? null : Integer.valueOf(methodId),
                status(req.getParameter("status")),
                page == null || page.isBlank() ? 1 : Math.max(1, Integer.parseInt(page)));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.remove("rows");
        String head = SalesJson.object(result);                     // {"total":..,"page":..,"pageSize":..}
        resp.getWriter().write(head.substring(0, head.length() - 1) + ",\"rows\":" + array(rows) + "}");
    }

    /* [153] */
    private void writeDetail(HttpServletRequest req, HttpServletResponse resp, long invoiceId) throws Exception {
        SalesInvoice invoice = orderDao.findInvoice(invoiceId);
        if (invoice == null) {
            fail(resp, 404, "Không tìm thấy hóa đơn");
            return;
        }
        String order = SalesJson.order(invoice, orderDao.findLines(invoiceId),
                orderDao.findPayments(invoiceId), orderDao.findAllocations(invoiceId));
                Integer userId = AuthSession.userId(req);
        Map<String, Object> edit = userId == null ? Map.of("canEdit", false)
                : editDao.editPermission(invoiceId, userId, isOwnerEditor(req));
        resp.getWriter().write("{\"order\":" + order
                + ",\"extra\":" + SalesJson.object(dao.findExtra(invoiceId))
                + ",\"edit\":" + SalesJson.object(new java.util.LinkedHashMap<>(edit))
                + ",\"canCancel\":" + canCancel(req) + "}");
    }

    /** Có quyền SALES_INVOICE_EDIT (chủ): sửa mọi đơn, không giới hạn thời gian */
    private static boolean isOwnerEditor(HttpServletRequest req) {
        return AuthSession.can(AuthSession.current(req), PERM_EDIT);
    }

    private static boolean canCancel(HttpServletRequest req) {
        HttpSession session = AuthSession.current(req);
        return AuthSession.can(session, PERM_CANCEL);
    }

    /* ---------- Hàm phụ ---------- */

    private static String array(List<Map<String, Object>> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(SalesJson.object(list.get(i)));
        }
        return sb.append(']').toString();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Chỉ nhận yyyy-MM-dd, sai định dạng thì báo lỗi 400 */
    private static String date(String s) {
        s = blankToNull(s);
        return s == null ? null : LocalDate.parse(s).toString();
    }

    private static String status(String s) {
        s = blankToNull(s);
        if (s == null) return null;
        if (!List.of("PENDING", "PAID", "CANCELLED").contains(s)) throw new NumberFormatException();
        return s;
    }

    private static void fail(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.getWriter().write(SalesJson.error(message));
    }
}