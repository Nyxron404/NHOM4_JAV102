package controllers.longdtta00178;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import dao.longdtta00178.VnpayDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * [161] Nhận kết quả từ VNPay.
 *   GET /api/vnpay/return  trình duyệt khách quay về sau khi trả tiền -> xử lý rồi chuyển sang trang kết quả
 *   GET /api/vnpay/ipn     máy chủ VNPay gọi thẳng (cần địa chỉ public) -> trả {"RspCode","Message"}
 * Cả 2 cùng gọi VnpayDAO.handleResult, gọi trùng nhiều lần vẫn an toàn (lần sau trả 02).
 */
@WebServlet(urlPatterns = {"/api/vnpay/return", "/api/vnpay/ipn"})
public class VnpayController extends HttpServlet {

    private final VnpayDAO dao = new VnpayDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        boolean isIpn = req.getServletPath().endsWith("/ipn");
        Map<String, String> params = VnpayConfig.vnpParams(req);
        boolean valid = VnpayConfig.verify(params);

        String code;
        try {
            code = dao.handleResult(params, isIpn ? "IPN" : "RETURN", valid,
                    SalesJson.object(new LinkedHashMap<>(params)));
        } catch (Exception e) {
            e.printStackTrace();
            code = "99";
        }

        if (isIpn) {
            resp.setContentType("application/json;charset=UTF-8");
            String message = switch (code) {
                case "00" -> "Confirm Success";
                case "01" -> "Order not found";
                case "02" -> "Order already confirmed";
                case "04" -> "Invalid amount";
                case "97" -> "Invalid signature";
                default -> "Unknown error";
            };
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("RspCode", code);
            body.put("Message", message);
            resp.getWriter().write(SalesJson.object(body));
            return;
        }

        // Return URL: chuyển sang trang kết quả cho người trả tiền xem
        String result;
        if (!valid) {
            result = "invalid";
        } else if ("00".equals(params.get("vnp_ResponseCode")) && "00".equals(params.get("vnp_TransactionStatus"))) {
            result = "success";
        } else {
            result = "failed";
        }
        String amount = params.getOrDefault("vnp_Amount", "0");
        String url = req.getContextPath() + "/pages/longdtta00178/longdtta001783.html"
                + "?result=" + result
                + "&txnRef=" + URLEncoder.encode(params.getOrDefault("vnp_TxnRef", ""), StandardCharsets.UTF_8)
                + "&amount=" + (amount.length() > 2 ? amount.substring(0, amount.length() - 2) : "0")
                + "&code=" + URLEncoder.encode(params.getOrDefault("vnp_ResponseCode", ""), StandardCharsets.UTF_8);
        resp.sendRedirect(url);
    }
}