package controllers.minhpvth08530;

import dao.minhpvth08530.AccountDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Quản lý tài khoản — UC8..UC17. Chỉ tài khoản có quyền USER_MANAGE (chủ cửa hàng) mới gọi được.
 * <pre>
 * GET  /api/accounts?view=users&amp;keyword=&amp;roleId=&amp;status=&amp;page=   UC8, UC9 (status=LOCKED cho UC13)
 * GET  /api/accounts?view=user&amp;id=                                    UC11 B1
 * GET  /api/accounts?view=roles                                       UC10 B1, UC15 B1
 * GET  /api/accounts?view=rolePermissions&amp;roleId=                     UC15 B3
 * GET  /api/accounts?view=logins&amp;userId=&amp;keyword=&amp;success=&amp;from=&amp;to=&amp;page=   UC16
 * GET  /api/accounts?view=emails&amp;keyword=&amp;status=&amp;template=&amp;from=&amp;to=&amp;page=  UC17
 * POST /api/accounts action=
 *      create roleId, username, password, fullName, email, phone   (UC10)
 *      update id, fullName, email, phone                           (UC11)
 *      lock id, reason (UC12) | unlock id, reason (UC13)
 *      resetPassword id                                            (UC14)
 *      changeRole id, roleId                                       (UC15)
 * </pre>
 */
@WebServlet("/api/accounts")
public class AccountController extends BaseController {

    private final AccountDAO accountDAO = new AccountDAO();

    @Override
    protected Object handleGet(HttpServletRequest request) throws Exception {
        requireManager(request);
        String view = text(request, "view");
        int page = Math.max(1, optionalInt(request, "page") == null ? 1 : optionalInt(request, "page"));
        Integer sizeParam = optionalInt(request, "size");
        int size = sizeParam == null ? 20 : Math.min(100, Math.max(5, sizeParam));
        switch (view == null ? "users" : view) {
            case "user" -> {
                return found(accountDAO.findUser(requiredInt(request, "id", "mã tài khoản")), "tài khoản");
            }
            case "roles" -> {
                return accountDAO.roles();
            }
            case "rolePermissions" -> {
                return accountDAO.rolePermissions(requiredInt(request, "roleId", "vai trò"));
            }
            case "logins" -> {
                String success = text(request, "success");
                return accountDAO.loginHistory(optionalInt(request, "userId"), text(request, "keyword"),
                        success == null ? null : Boolean.valueOf(success), date(request, "from"),
                        date(request, "to"), page, size);
            }
            case "emails" -> {
                return accountDAO.emailLogs(text(request, "keyword"), text(request, "status"),
                        text(request, "template"), date(request, "from"), date(request, "to"), page, size);
            }
            default -> {
                return accountDAO.searchUsers(text(request, "keyword"), optionalInt(request, "roleId"),
                        text(request, "status"), page, size);
            }
        }
    }

    @Override
    protected Object handlePost(HttpServletRequest request) throws Exception {
        int adminId = requireManager(request);
        String action = action(request);
        if ("create".equals(action)) {
            int id = accountDAO.createUser(adminId, optionalInt(request, "roleId"), request.getParameter("username"),
                    request.getParameter("password"), request.getParameter("fullName"),
                    request.getParameter("email"), request.getParameter("phone"));
            return ok("Đã thêm tài khoản", "id", id);
        }
        if ("save".equals(action)) {
            // Form "Thêm mới / Cập nhật nhân viên" (gộp UC10, UC11, UC12, UC13, UC14, UC15)
            String password = request.getParameter("password");
            if (password != null && !password.isEmpty() && !password.equals(request.getParameter("confirmPassword"))) {
                throw new IllegalArgumentException("Mật khẩu nhập lại không khớp");
            }
            Integer id = optionalInt(request, "id");
            Map<String, Object> result = accountDAO.saveUser(adminId, id, request.getParameter("username"), password,
                    request.getParameter("fullName"), request.getParameter("email"), request.getParameter("phone"),
                    "true".equals(request.getParameter("active")), "true".equals(request.getParameter("manager")));
            if (Boolean.TRUE.equals(result.get("sessionChanged"))) {
                AuthSession.revoke((Integer) result.get("id"));   // tài khoản đang mở ở máy khác phải đăng nhập lại
            }
            return ok(id == null ? "Đã thêm nhân viên" : "Đã cập nhật nhân viên", "id", result.get("id"));
        }
        int id = requiredInt(request, "id", "mã tài khoản");
        switch (action) {
            case "delete" -> {
                accountDAO.deleteUser(adminId, id);
                AuthSession.revoke(id);
                return ok("Đã xóa nhân viên");
            }
            case "update" -> {
                accountDAO.updateUser(adminId, id, request.getParameter("fullName"),
                        request.getParameter("email"), request.getParameter("phone"));
                return ok("Đã cập nhật tài khoản");
            }
            case "lock" -> {
                accountDAO.lockUser(adminId, id, request.getParameter("reason"));
                AuthSession.revoke(id);   // đang đăng nhập ở máy nào cũng bị đăng xuất
                return ok("Đã khóa tài khoản");
            }
            case "unlock" -> {
                accountDAO.unlockUser(adminId, id, request.getParameter("reason"));
                return ok("Đã mở khóa tài khoản");
            }
            case "resetPassword" -> {
                String temporary = accountDAO.adminResetPassword(adminId, id);
                if (id != adminId) {
                    AuthSession.revoke(id);
                }
                Map<String, Object> body = ok("Đã đặt lại mật khẩu. Mật khẩu tạm chỉ hiện một lần.");
                body.put("temporaryPassword", temporary);
                return body;
            }
            case "changeRole" -> {
                accountDAO.changeRole(adminId, id, optionalInt(request, "roleId"));
                AuthSession.revoke(id);   // nạp lại quyền mới ở lần đăng nhập sau
                return ok("Đã đổi vai trò, tài khoản cần đăng nhập lại để nhận quyền mới");
            }
            default -> throw new IllegalArgumentException("Thao tác không hợp lệ: " + action);
        }
    }

    private static int requireManager(HttpServletRequest request) {
        HttpSession session = AuthController.requireSession(request);
        if (!AuthSession.can(session, AccountDAO.MANAGE_PERMISSION)) {
            throw new ApiException(403, "Chỉ chủ cửa hàng mới quản lý được tài khoản");
        }
        return (Integer) session.getAttribute(AuthSession.USER_ID);
    }

    private static LocalDate date(HttpServletRequest request, String name) {
        String value = text(request, name);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Ngày không hợp lệ");
        }
    }
}
