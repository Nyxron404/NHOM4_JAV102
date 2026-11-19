package controllers.hieulnmth08470;

import dao.hieulnmth08470.NotificationDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * API thông báo — UC113, UC125..UC129 (Lê Nguyễn Minh Hiếu). Trang: pages/hieulnmth08470/thong-bao.html
 * <p>Thông báo dành cho chủ cửa hàng (quyền INVENTORY_MANAGE), giống chú thích bảng notification_reads.</p>
 * <pre>
 * GET  /api/thong-bao?view=count                                 số chưa đọc (chuông trên header)
 * GET  /api/thong-bao?view=list&type=&unread=true&page=&size=    UC127
 * GET  /api/thong-bao?view=detail&id=                            UC128
 * POST action=read&id=                                           UC129
 * POST action=readAll                                            đánh dấu tất cả đã đọc
 * POST action=scan                                               chạy ngay UC113 + UC125 + UC126
 * </pre>
 */
@WebServlet("/api/thong-bao")
public class NotificationController extends InventoryBaseController {

    private final NotificationDAO notifications = new NotificationDAO();

    @Override
    protected Object handleGet(HttpServletRequest request) throws Exception {
        String view = text(request, "view");
        if ("count".equals(view)) {
            // Nhân viên không có quyền: trả 0 để header ẩn số, không báo lỗi
            return Map.of("unread", canManage(request) ? notifications.unreadCount(currentUserId(request)) : 0,
                    "canView", canManage(request));
        }
        requireManager(request);
        int userId = currentUserId(request);
        return switch (view == null ? "list" : view) {
            case "list" -> notifications.list(userId, text(request, "type"),
                    "true".equals(text(request, "unread")), optionalInt(request, "page"), optionalInt(request, "size"));
            case "detail" -> found(notifications.detail(requiredLong(request, "id", "mã thông báo"), userId),
                    "thông báo");
            default -> throw new IllegalArgumentException("view không hợp lệ");
        };
    }

    @Override
    protected Object handlePost(HttpServletRequest request) throws Exception {
        String action = action(request);
        requireManager(request);
        int userId = currentUserId(request);
        return switch (action) {
            case "read" -> {
                notifications.markRead(requiredLong(request, "id", "mã thông báo"), userId);
                yield ok("Đã đánh dấu đã đọc");
            }
            case "readAll" -> {
                int count = notifications.markAllRead(userId);
                yield ok("Đã đánh dấu " + count + " thông báo là đã đọc");
            }
            case "scan" -> {
                Map<String, Object> result = notifications.detectAll();
                int total = ((Number) result.get("expired")).intValue() + ((Number) result.get("nearExpiry")).intValue()
                        + ((Number) result.get("lowStock")).intValue();
                yield ok(total == 0 ? "Không có cảnh báo mới" : "Có " + total + " thông báo mới", "created", result);
            }
            default -> throw new IllegalArgumentException("action không hợp lệ");
        };
    }
}
