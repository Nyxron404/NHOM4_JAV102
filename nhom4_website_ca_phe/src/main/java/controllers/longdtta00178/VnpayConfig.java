package controllers.longdtta00178;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Cấu hình + ký / kiểm chữ ký VNPay (chuẩn vnp_Version 2.1.0, HMAC-SHA512).
 *
 * Đọc 2 biến môi trường (KHÔNG ghi khóa vào code, repo đang public):
 *   VNP_TMN_CODE     mã website VNPay cấp
 *   VNP_HASH_SECRET  chuỗi bí mật VNPay cấp
 *   VNP_URL          (không bắt buộc) mặc định https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
 * Chưa đặt VNP_TMN_CODE / VNP_HASH_SECRET -> chạy CỔNG GIẢ LẬP (/mock-vnpay/pay) để test.
 */
public final class VnpayConfig {

    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final DateTimeFormatter VNP_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** QR / link thanh toán hết hạn sau bao nhiêu phút */
    public static final int EXPIRE_MINUTES = 15;

    private static final String ENV_TMN = System.getenv("VNP_TMN_CODE");
    private static final String ENV_SECRET = System.getenv("VNP_HASH_SECRET");
    private static final String ENV_URL = System.getenv("VNP_URL");

    private static final String MOCK_TMN = "MOCK0001";
    private static final String MOCK_SECRET = "MOCKSECRETCHIDUNGDETESTKHONGPHAIKHOATHAT";

    private VnpayConfig() {}

    /** true = chưa cấu hình mã thật -> dùng cổng giả lập */
    public static boolean isMock() {
        return ENV_TMN == null || ENV_TMN.isBlank() || ENV_SECRET == null || ENV_SECRET.isBlank();
    }

    public static String tmnCode() { return isMock() ? MOCK_TMN : ENV_TMN.trim(); }

    private static String hashSecret() { return isMock() ? MOCK_SECRET : ENV_SECRET.trim(); }

    /** http://localhost:8080/nhom4_website_ca_phe */
    public static String baseUrl(HttpServletRequest req) {
        String env = System.getenv("APP_BASE_URL");
        if (env != null && !env.isBlank()) return env.strip().replaceAll("/+$", "");
        // Sau proxy (Railway, Render...) Tomcat chỉ thấy http nội bộ -> đọc X-Forwarded-*
        String proto = req.getHeader("X-Forwarded-Proto");
        String scheme = proto != null && !proto.isBlank() ? proto.split(",")[0].trim() : req.getScheme();
        String fwdHost = req.getHeader("X-Forwarded-Host");
        String host = fwdHost != null && !fwdHost.isBlank() ? fwdHost.split(",")[0].trim() : req.getServerName();
        int port = req.getServerPort();
        boolean behindProxy = proto != null && !proto.isBlank();
        boolean defaultPort = behindProxy || host.contains(":")
                || ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port) + req.getContextPath();
    }

    public static String payUrl(HttpServletRequest req) {
        if (isMock()) return baseUrl(req) + "/mock-vnpay/pay";
        return ENV_URL == null || ENV_URL.isBlank()
                ? "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html" : ENV_URL.trim();
    }

    public static String returnUrl(HttpServletRequest req) {
        return baseUrl(req) + "/api/vnpay/return";
    }

    public static String now() {
        return LocalDateTime.now(VIETNAM).format(VNP_DATE);
    }

    public static String plusMinutes(int minutes) {
        return LocalDateTime.now(VIETNAM).plusMinutes(minutes).format(VNP_DATE);
    }

    /** IP người dùng (VNPay bắt buộc có vnp_IpAddr) */
    public static String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : req.getRemoteAddr();
        return "0:0:0:0:0:0:0:1".equals(ip) ? "127.0.0.1" : ip;
    }

    /* ---------- Ký và kiểm chữ ký ---------- */

    /** Chuỗi để ký: các tham số khác rỗng, sắp xếp theo tên, giá trị URL-encode (giống code mẫu Java của VNPay) */
    private static String hashData(Map<String, String> params) {
        List<String> names = new ArrayList<>(params.keySet());
        Collections.sort(names);
        StringBuilder sb = new StringBuilder();
        for (String name : names) {
            String value = params.get(name);
            if (value == null || value.isEmpty()) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(name).append('=').append(encode(value));
        }
        return sb.toString();
    }

    /** Tạo query string đã ký: ...&vnp_SecureHash=... */
    public static String signedQuery(Map<String, String> params) {
        List<String> names = new ArrayList<>(params.keySet());
        Collections.sort(names);
        StringBuilder query = new StringBuilder();
        for (String name : names) {
            String value = params.get(name);
            if (value == null || value.isEmpty()) continue;
            if (query.length() > 0) query.append('&');
            query.append(encode(name)).append('=').append(encode(value));
        }
        return query + "&vnp_SecureHash=" + hmacSha512(hashSecret(), hashData(params));
    }

    /** Kiểm chữ ký các tham số VNPay gửi về (Return URL / IPN) */
    public static boolean verify(Map<String, String> params) {
        String received = params.get("vnp_SecureHash");
        if (received == null) return false;
        Map<String, String> data = new java.util.HashMap<>(params);
        data.remove("vnp_SecureHash");
        data.remove("vnp_SecureHashType");
        return hmacSha512(hashSecret(), hashData(data)).equalsIgnoreCase(received);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private static String hmacSha512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Không ký được dữ liệu VNPay", e);
        }
    }

    /** Lấy tất cả tham số vnp_* của request thành Map */
    public static Map<String, String> vnpParams(HttpServletRequest req) {
        Map<String, String> map = new java.util.TreeMap<>();
        req.getParameterMap().forEach((name, values) -> {
            if (name.startsWith("vnp_") && values.length > 0) map.put(name, values[0]);
        });
        return map;
    }
}