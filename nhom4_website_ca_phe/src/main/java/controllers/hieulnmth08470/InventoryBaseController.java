package controllers.hieulnmth08470;

import controllers.minhpvth08530.ApiException;
import controllers.minhpvth08530.AuthSession;
import controllers.minhpvth08530.BaseController;
import dao.hieulnmth08470.InventoryDAO;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp cha của các API phần Tồn kho, kiểm kê và thông báo (Lê Nguyễn Minh Hiếu).
 * <p>Dùng lại khung controllers.minhpvth08530.BaseController (trả JSON, đổi lỗi thành câu tiếng Việt),
 * thêm hàm đọc số Long, ngày và kiểm tra quyền INVENTORY_MANAGE.</p>
 */
abstract class InventoryBaseController extends BaseController {

    protected static Long optionalLong(HttpServletRequest request, String name) {
        String value = text(request, name);
        return value == null ? null : Long.valueOf(value.strip());
    }

    protected static long requiredLong(HttpServletRequest request, String name, String label) {
        Long value = optionalLong(request, name);
        if (value == null) {
            throw new IllegalArgumentException("Thiếu " + label);
        }
        return value;
    }

    /** Ô input type=date ("2026-11-19"); rỗng = null. */
    protected static LocalDate date(HttpServletRequest request, String name) {
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

    /** Danh sách số cách nhau bởi dấu phẩy ("3,5,8"). */
    protected static List<Long> longList(HttpServletRequest request, String name) {
        List<Long> values = new ArrayList<>();
        String value = text(request, name);
        if (value != null) {
            for (String part : value.split(",")) {
                if (!part.isBlank()) {
                    values.add(Long.valueOf(part.strip()));
                }
            }
        }
        return values;
    }

    protected static boolean canManage(HttpServletRequest request) {
        return AuthSession.can(request.getSession(false), InventoryDAO.MANAGE_PERMISSION);
    }

    /** Chỉ chủ cửa hàng (quyền INVENTORY_MANAGE) mới được làm thao tác này. */
    protected static void requireManager(HttpServletRequest request) {
        if (!canManage(request)) {
            throw new ApiException(403, "Chỉ chủ cửa hàng (quyền quản lý kho) mới làm được thao tác này");
        }
    }
}
