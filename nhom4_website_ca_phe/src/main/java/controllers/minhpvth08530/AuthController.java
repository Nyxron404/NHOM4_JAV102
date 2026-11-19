package controllers.minhpvth08530;

import dao.minhpvth08530.AccountDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Đăng nhập, đăng xuất, mật khẩu và tài khoản của tôi — UC1..UC7.
 * <pre>
 * GET  /api/auth?action=me                    phiên hiện tại (401 nếu chưa đăng nhập)
 * GET  /api/auth?action=profile               UC6 thông tin tài khoản + quyền
 * GET  /api/auth?action=checkToken&amp;token=     UC4 B1 kiểm tra liên kết đặt lại mật khẩu
 * POST /api/auth action=
 *      login username, password                   (UC1)
 *      logout                                     (UC2)
 *      forgot email                               (UC3)
 *      reset token, newPassword, confirmPassword  (UC4)
 *      changePassword currentPassword, newPassword, confirmPassword (UC5)
 *      updateProfile fullName, email, phone       (UC7)
 * </pre>
 * Bộ lọc AuthFilter cho phép gọi /api/auth khi chưa đăng nhập; các thao tác cần phiên tự kiểm tra ở đây.
 */
@WebServlet("/api/auth")
public class AuthController extends BaseController {

    private final AccountDAO accountDAO = new AccountDAO();

    @Override
    protected Object handleGet(HttpServletRequest request) throws Exception {
        String action = text(request, "action");
        if ("checkToken".equals(action)) {
            String username = accountDAO.checkResetToken(text(request, "token"));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("valid", username != null);
            body.put("username", username);
            return body;
        }
        HttpSession session = requireSession(request);
        if ("profile".equals(action)) {
            return found(accountDAO.profile((Integer) session.getAttribute(AuthSession.USER_ID)), "tài khoản");
        }
        return AuthSession.describe(session, AccountDAO.MANAGE_PERMISSION);
    }

    @Override
    protected Object handlePost(HttpServletRequest request) throws Exception {
        String action = action(request);
        switch (action) {
            case "login" -> {
                Map<String, Object> user = accountDAO.login(request.getParameter("username"),
                        request.getParameter("password"), clientIp(request), request.getHeader("User-Agent"));
                AuthSession.start(request, user);
                Map<String, Object> body = ok("Đăng nhập thành công");
                body.put("user", AuthSession.describe(request.getSession(false), AccountDAO.MANAGE_PERMISSION));
                return body;
            }
            case "logout" -> {
                HttpSession session = AuthSession.current(request);
                if (session != null) {
                    Object loginId = session.getAttribute(AuthSession.LOGIN_ID);
                    if (loginId instanceof Number id) {
                        accountDAO.logout(id.longValue(), (Integer) session.getAttribute(AuthSession.USER_ID));
                    }
                    session.invalidate();
                }
                return ok("Đã đăng xuất");
            }
            case "forgot" -> {
                throttleForgot(request);
                Map<String, Object> user = accountDAO.createResetToken(request.getParameter("email"));
                if (user != null) {
                    String link = baseUrl(request) + "/pages/minhpvth08530/dang-nhap.html?token="
                            + URLEncoder.encode((String) user.get("token"), StandardCharsets.UTF_8);
                    Mailer.sendPasswordReset((Integer) user.get("userId"), (String) user.get("fullName"),
                            (String) user.get("email"), link);
                }
                // Luôn trả cùng một câu để không lộ email nào có tài khoản
                return ok("Nếu email thuộc một tài khoản đang hoạt động, hệ thống đã gửi liên kết đặt lại mật khẩu. "
                        + "Liên kết có hiệu lực 15 phút.");
            }
            case "reset" -> {
                String password = request.getParameter("newPassword");
                confirm(password, request.getParameter("confirmPassword"));
                accountDAO.resetPassword(request.getParameter("token"), password);
                return ok("Đã đặt lại mật khẩu, hãy đăng nhập bằng mật khẩu mới");
            }
            case "changePassword" -> {
                HttpSession session = requireSession(request);
                String password = request.getParameter("newPassword");
                confirm(password, request.getParameter("confirmPassword"));
                accountDAO.changePassword((Integer) session.getAttribute(AuthSession.USER_ID),
                        request.getParameter("currentPassword"), password);
                return ok("Đã đổi mật khẩu");
            }
            case "updateProfile" -> {
                HttpSession session = requireSession(request);
                accountDAO.updateProfile((Integer) session.getAttribute(AuthSession.USER_ID),
                        request.getParameter("fullName"), request.getParameter("email"),
                        request.getParameter("phone"));
                session.setAttribute(AuthSession.FULL_NAME,
                        java.text.Normalizer.normalize(request.getParameter("fullName"),
                                java.text.Normalizer.Form.NFC).strip());
                return ok("Đã cập nhật thông tin cá nhân");
            }
            default -> throw new IllegalArgumentException("Thao tác không hợp lệ: " + action);
        }
    }

    /** Mỗi máy (IP) chỉ được yêu cầu gửi email quên mật khẩu 1 lần / 15 giây, chống bấm liên tục. */
    private static final long FORGOT_COOLDOWN_MS = 15_000;
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> LAST_FORGOT =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static void throttleForgot(HttpServletRequest request) {
        long now = System.currentTimeMillis();
        String key = String.valueOf(clientIp(request));
        Long last = LAST_FORGOT.get(key);
        if (last != null && now - last < FORGOT_COOLDOWN_MS) {
            long seconds = (FORGOT_COOLDOWN_MS - (now - last) + 999) / 1000;
            throw new ApiException(429, "Bạn vừa yêu cầu rồi, vui lòng đợi " + seconds + " giây rồi thử lại");
        }
        LAST_FORGOT.put(key, now);
        if (LAST_FORGOT.size() > 5000) {   // dọn bớt để bộ nhớ không phình
            LAST_FORGOT.entrySet().removeIf(entry -> now - entry.getValue() > FORGOT_COOLDOWN_MS);
        }
    }

    static HttpSession requireSession(HttpServletRequest request) {
        HttpSession session = AuthSession.current(request);
        if (session == null) {
            throw new ApiException(401, "Phiên đăng nhập đã hết, hãy đăng nhập lại");
        }
        return session;
    }

    private static void confirm(String password, String confirmation) {
        if (password == null || !password.equals(confirmation)) {
            throw new IllegalArgumentException("Mật khẩu nhập lại không khớp");
        }
    }

    private static String baseUrl(HttpServletRequest request) {
        String configured = Mailer.baseUrl();
        boolean localRequest = request.getServerName().equals("localhost")
                || request.getServerName().startsWith("127.");
        // Ưu tiên địa chỉ cấu hình (tránh giả mạo Host), nhưng bỏ qua giá trị localhost khi đang chạy trên server thật
        if (configured != null && (localRequest || !configured.contains("://localhost"))) {
            return configured;
        }
        return controllers.longdtta00178.VnpayConfig.baseUrl(request);
    }

    /** IP người dùng cho login_histories (bỏ phần "%zone" của IPv6 vì kiểu INET không nhận). */
    private static String clientIp(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (ip == null) {
            return null;
        }
        int zone = ip.indexOf('%');
        return zone >= 0 ? ip.substring(0, zone) : ip;
    }
}
