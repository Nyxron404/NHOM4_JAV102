package controllers.minhpvth08530;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phiên đăng nhập lưu trong HttpSession. Thuộc tính "userId" giữ đúng tên mà các phần khác
 * (bán hàng, sản phẩm) đang đọc để biết người thao tác.
 */
public final class AuthSession {

    public static final String USER_ID = "userId";
    public static final String LOGIN_ID = "loginId";
    public static final String USERNAME = "username";
    public static final String FULL_NAME = "fullName";
    public static final String ROLE_CODE = "roleCode";
    public static final String ROLE_NAME = "roleName";
    public static final String PERMISSIONS = "permissions";
    public static final String LOGIN_AT = "loginAt";

    /**
     * Thời điểm tài khoản bị khóa / đổi vai trò / đặt lại mật khẩu. Phiên đăng nhập trước thời điểm này
     * bị buộc đăng nhập lại (bộ lọc AuthFilter kiểm tra mỗi lần gọi, không cần hỏi database).
     */
    private static final Map<Integer, Long> REVOKED = new ConcurrentHashMap<>();

    private AuthSession() {}

    /** Tạo phiên mới sau khi đăng nhập đúng (đổi session id để chống chiếm phiên). */
    public static void start(HttpServletRequest request, Map<String, Object> user) {
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(USER_ID, user.get("userId"));
        session.setAttribute(LOGIN_ID, user.get("loginId"));
        session.setAttribute(USERNAME, user.get("username"));
        session.setAttribute(FULL_NAME, user.get("fullName"));
        session.setAttribute(ROLE_CODE, user.get("roleCode"));
        session.setAttribute(ROLE_NAME, user.get("roleName"));
        @SuppressWarnings("unchecked")
        Collection<String> permissions = (Collection<String>) user.get("permissions");
        session.setAttribute(PERMISSIONS, Set.copyOf(permissions));
        session.setAttribute(LOGIN_AT, System.currentTimeMillis());
    }

    /** Phiên hợp lệ hiện tại, hoặc null nếu chưa đăng nhập / phiên đã bị thu hồi. */
    public static HttpSession current(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(USER_ID) instanceof Integer userId)) {
            return null;
        }
        Long revokedAt = REVOKED.get(userId);
        Object loginAt = session.getAttribute(LOGIN_AT);
        if (revokedAt != null && loginAt instanceof Long time && time < revokedAt) {
            session.invalidate();
            return null;
        }
        return session;
    }

    public static Integer userId(HttpServletRequest request) {
        HttpSession session = current(request);
        return session == null ? null : (Integer) session.getAttribute(USER_ID);
    }

    public static boolean can(HttpSession session, String permission) {
        return session != null && session.getAttribute(PERMISSIONS) instanceof Set<?> set
                && set.contains(permission);
    }

    /** Buộc mọi phiên đang mở của tài khoản phải đăng nhập lại. */
    public static void revoke(int userId) {
        REVOKED.put(userId, System.currentTimeMillis());
    }

    /** Thông tin phiên trả cho trang web (header, kiểm tra quyền). */
    public static Map<String, Object> describe(HttpSession session, String managePermission) {
        Map<String, Object> me = new LinkedHashMap<>();
        me.put("userId", session.getAttribute(USER_ID));
        me.put("username", session.getAttribute(USERNAME));
        me.put("fullName", session.getAttribute(FULL_NAME));
        me.put("roleCode", session.getAttribute(ROLE_CODE));
        me.put("roleName", session.getAttribute(ROLE_NAME));
        Object permissions = session.getAttribute(PERMISSIONS);
        me.put("permissions", permissions instanceof Set<?> set ? List.copyOf(set) : List.of());
        me.put("canManageAccounts", can(session, managePermission));
        return me;
    }
}
