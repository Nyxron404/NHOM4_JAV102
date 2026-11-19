package filters;

import controllers.minhpvth08530.AuthSession;
import dao.minhpvth08530.AccountDAO;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Chặn truy cập khi chưa đăng nhập (UC1).
 * <ul>
 *   <li>Trang đăng nhập, ảnh/CSS/JS và /api/auth luôn mở.</li>
 *   <li>Chưa đăng nhập: trang web chuyển về trang đăng nhập (kèm ?next= để quay lại), API trả 401.</li>
 *   <li>Trang quản lý tài khoản và /api/accounts chỉ cho tài khoản có quyền USER_MANAGE.</li>
 * </ul>
 */
@WebFilter("/*")
public class AuthFilter implements Filter {

    private static final String LOGIN_PAGE = "/pages/minhpvth08530/dang-nhap.html";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String path = request.getRequestURI().substring(request.getContextPath().length());

        if (isPublic(path)) {
            chain.doFilter(req, res);
            return;
        }
        HttpSession session = AuthSession.current(request);
        boolean api = path.startsWith("/api/");
        if (session == null) {
            if (api) {
                json(response, 401, "Phiên đăng nhập đã hết, hãy đăng nhập lại");
            } else {
                String next = request.getRequestURI()
                        + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
                response.sendRedirect(request.getContextPath() + LOGIN_PAGE + "?next="
                        + URLEncoder.encode(next, StandardCharsets.UTF_8));
            }
            return;
        }
        if (isManagerOnly(path) && !AuthSession.can(session, AccountDAO.MANAGE_PERMISSION)) {
            if (api) {
                json(response, 403, "Chỉ chủ cửa hàng mới quản lý được tài khoản");
            } else {
                response.sendRedirect(request.getContextPath() + "/index.html");
            }
            return;
        }
        chain.doFilter(req, res);
    }

    private static boolean isPublic(String path) {
        return path.equals(LOGIN_PAGE)
                || path.startsWith("/assets/")
                || path.equals("/api/auth")
                                || path.equals("/favicon.ico")
                // VNPay: có kiểm chữ ký HMAC, không cần đăng nhập
                || path.equals("/api/vnpay/ipn")
                || path.equals("/api/vnpay/return")
                || path.equals("/mock-vnpay/pay")
                || path.equals("/pages/longdtta00178/vnpay-ket-qua.html");
    }

    private static boolean isManagerOnly(String path) {
        return path.equals("/pages/minhpvth08530/tai-khoan.html") || path.startsWith("/api/accounts");
    }

    private static void json(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"ok\":false,\"error\":\"" + message + "\"}");
    }
}
