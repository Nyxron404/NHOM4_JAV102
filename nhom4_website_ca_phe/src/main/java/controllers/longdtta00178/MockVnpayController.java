package controllers.longdtta00178;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import dao.longdtta00178.VnpayDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * CỔNG THANH TOÁN GIẢ LẬP (chỉ chạy khi chưa đặt VNP_TMN_CODE / VNP_HASH_SECRET).
 * Mô phỏng đúng luồng VNPay sandbox để test khi chưa có tài khoản:
 *   GET  /mock-vnpay/pay?vnp_...   kiểm chữ ký link thanh toán, hiện trang có 2 nút Thành công / Thất bại
 *   POST /mock-vnpay/pay           tạo kết quả có chữ ký như VNPay thật, gửi "IPN" (gọi thẳng VnpayDAO),
 *                                  rồi chuyển trình duyệt về vnp_ReturnUrl
 * Có mã thật thì trang này tự tắt (trả 404), link thanh toán trỏ sang VNPay sandbox.
 */
@WebServlet("/mock-vnpay/pay")
public class MockVnpayController extends HttpServlet {

    private final VnpayDAO dao = new VnpayDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!VnpayConfig.isMock()) {
            resp.sendError(404);
            return;
        }
        Map<String, String> params = VnpayConfig.vnpParams(req);
        boolean valid = VnpayConfig.verify(params);
        long amount = parseAmount(params.get("vnp_Amount")) / 100;

        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        out.println("<!doctype html><html lang='vi'><head><meta charset='UTF-8' />"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0' />"
                + "<title>Cổng thanh toán giả lập</title><style>"
                + "body{font-family:Arial,sans-serif;background:#FAF7F2;color:#2C1E16;margin:0;padding:30px}"
                + ".box{max-width:420px;margin:0 auto;background:#fff;border:1px solid #EAE2D6;border-radius:14px;padding:24px}"
                + ".tag{display:inline-block;background:#FEF3E2;color:#B45F04;font-size:12px;font-weight:bold;padding:3px 10px;border-radius:20px}"
                + "h2{margin:12px 0 4px}.amount{font-size:30px;font-weight:bold;color:#D97706;margin:16px 0}"
                + ".row{display:flex;justify-content:space-between;font-size:14px;padding:4px 0;border-bottom:1px dashed #EAE2D6}"
                + "button{width:100%;padding:13px;margin-top:12px;border:none;border-radius:8px;font-size:16px;font-weight:bold;cursor:pointer}"
                + ".ok{background:#1E7B3C;color:#fff}.fail{background:#fff;color:#B42318;border:1px solid #B42318}"
                + ".err{color:#B42318;font-weight:bold}</style></head><body><div class='box'>");
        out.println("<span class='tag'>MÔI TRƯỜNG GIẢ LẬP · KHÔNG TRỪ TIỀN THẬT</span>");
        out.println("<h2>Cổng thanh toán giả lập</h2>");

        if (!valid) {
            out.println("<p class='err'>Sai chữ ký (vnp_SecureHash). Link thanh toán không hợp lệ.</p></div></body></html>");
            return;
        }

        out.println("<div class='amount'>" + String.format("%,d", amount).replace(',', '.') + "đ</div>");
        out.println(row("Mã giao dịch", params.get("vnp_TxnRef")));
        out.println(row("Nội dung", params.get("vnp_OrderInfo")));
        out.println(row("Mã website", params.get("vnp_TmnCode")));
        out.println(row("Hết hạn", params.get("vnp_ExpireDate")));

        // 2 nút: gửi lại các tham số cần thiết + kết quả muốn giả lập
        for (String result : new String[] {"success", "fail"}) {
            out.println("<form method='post'>");
            for (String key : new String[] {"vnp_TxnRef", "vnp_Amount", "vnp_OrderInfo", "vnp_ReturnUrl", "vnp_TmnCode"}) {
                out.println("<input type='hidden' name='" + key + "' value='" + escape(params.get(key)) + "' />");
            }
            out.println("<input type='hidden' name='result' value='" + result + "' />");
            out.println("success".equals(result)
                    ? "<button class='ok'>✔ Thanh toán thành công (thẻ NCB test)</button>"
                    : "<button class='fail'>✖ Khách hủy / thanh toán thất bại</button>");
            out.println("</form>");
        }
        out.println("</div></body></html>");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!VnpayConfig.isMock()) {
            resp.sendError(404);
            return;
        }
        req.setCharacterEncoding("UTF-8");
        boolean success = "success".equals(req.getParameter("result"));

        // Kết quả giống VNPay trả về
        Map<String, String> result = new LinkedHashMap<>();
        result.put("vnp_Amount", req.getParameter("vnp_Amount"));
        result.put("vnp_BankCode", "NCB");
        result.put("vnp_BankTranNo", "VNP" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        result.put("vnp_CardType", "ATM");
        result.put("vnp_OrderInfo", req.getParameter("vnp_OrderInfo"));
        result.put("vnp_PayDate", VnpayConfig.now());
        result.put("vnp_ResponseCode", success ? "00" : "24");          // 24 = khách hủy giao dịch
        result.put("vnp_TmnCode", req.getParameter("vnp_TmnCode"));
        result.put("vnp_TransactionNo", String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999)));
        result.put("vnp_TransactionStatus", success ? "00" : "02");
        result.put("vnp_TxnRef", req.getParameter("vnp_TxnRef"));

        String query = VnpayConfig.signedQuery(result);

        // Giả lập IPN: VNPay thật gọi máy chủ mình; ở đây gọi thẳng cùng hàm xử lý
        Map<String, String> signed = new LinkedHashMap<>(result);
        signed.put("vnp_SecureHash", query.substring(query.lastIndexOf('=') + 1));
        try {
            dao.handleResult(signed, "IPN", VnpayConfig.verify(signed), SalesJson.object(new LinkedHashMap<>(signed)));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Chuyển trình duyệt về Return URL như VNPay
        resp.sendRedirect(req.getParameter("vnp_ReturnUrl") + "?" + query);
    }

    private static String row(String label, String value) {
        return "<div class='row'><span>" + label + "</span><b>" + escape(value) + "</b></div>";
    }

    private static long parseAmount(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", "&#39;").replace("\"", "&quot;");
    }
}