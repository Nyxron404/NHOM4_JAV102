package controllers.hieulnmth08470;

import dao.hieulnmth08470.NotificationDAO;
import dao.hieulnmth08470.StockCountDAO;
import dao.hieulnmth08470.StockCountDAO.CountedLine;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API kiểm kê / điều chỉnh tồn — UC117..UC124 (Lê Nguyễn Minh Hiếu). Trang: pages/hieulnmth08470/kiem-ke.html
 * <pre>
 * GET  /api/kiem-ke?view=list&status=&type=            UC120
 * GET  /api/kiem-ke?view=detail&id=                    UC121 (kèm các dòng để nhập UC118)
 * GET  /api/kiem-ke?view=preview&packageId=            UC117-B1
 * POST action=create&packageId=&note=                  UC117-B2
 * POST action=save&id=&lineId=..&countedQty=..&lineNote=..  (lặp lại)   UC118
 * POST action=submit&id=                               UC119
 * POST action=approve&id=                              UC122 (chủ cửa hàng)
 * POST action=reject&id=&reason=                       UC123 (chủ cửa hàng)
 * POST action=adjust&lotId=&newQty=&reason=            UC124
 * </pre>
 */
@WebServlet("/api/kiem-ke")
public class StockCountController extends InventoryBaseController {

    private final StockCountDAO counts = new StockCountDAO();
    private final NotificationDAO notifications = new NotificationDAO();

    @Override
    protected Object handleGet(HttpServletRequest request) throws Exception {
        String view = text(request, "view");
        return switch (view == null ? "list" : view) {
            case "list" -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("rows", counts.search(text(request, "status"), text(request, "type")));
                body.put("canManage", canManage(request));
                body.put("userId", currentUserId(request));
                yield body;
            }
            case "detail" -> {
                Map<String, Object> body = found(counts.detail(requiredLong(request, "id", "mã phiếu")), "phiếu kiểm kê");
                body.put("canManage", canManage(request));
                yield body;
            }
            case "preview" -> counts.lotsToCount(optionalInt(request, "packageId"));
            default -> throw new IllegalArgumentException("view không hợp lệ");
        };
    }

    @Override
    protected Object handlePost(HttpServletRequest request) throws Exception {
        String action = action(request);
        int userId = currentUserId(request);
        return switch (action) {
            case "create" -> {
                long id = counts.createCount(optionalInt(request, "packageId"), text(request, "note"), userId);
                yield ok("Đã tạo phiếu kiểm kê #" + id, "stockCountId", id);
            }
            case "save" -> {
                int saved = counts.saveCounted(requiredLong(request, "id", "mã phiếu"), countedLines(request));
                yield ok("Đã lưu " + saved + " dòng", "saved", saved);
            }
            case "submit" -> {
                long id = requiredLong(request, "id", "mã phiếu");
                Map<String, Object> diff = counts.submit(id, userId);
                yield ok("Đã gửi báo cáo chênh lệch phiếu #" + id + " (" + diff.get("diffLines")
                        + " dòng lệch), chờ chủ cửa hàng duyệt", "diff", diff);
            }
            case "approve" -> {
                requireManager(request);
                int movements = counts.approve(requiredLong(request, "id", "mã phiếu"), userId);
                notifications.detectLowStock();
                yield ok(movements == 0 ? "Đã xác nhận phiếu, không có lô nào lệch"
                        : "Đã xác nhận và điều chỉnh tồn " + movements + " lô", "movements", movements);
            }
            case "reject" -> {
                requireManager(request);
                counts.reject(requiredLong(request, "id", "mã phiếu"), text(request, "reason"), userId);
                yield ok("Đã từ chối phiếu");
            }
            case "adjust" -> {
                long id = counts.requestAdjustment(requiredLong(request, "lotId", "lô"),
                        optionalInt(request, "newQty"), text(request, "reason"), userId);
                yield ok("Đã tạo phiếu điều chỉnh #" + id + ", chờ chủ cửa hàng duyệt", "stockCountId", id);
            }
            default -> throw new IllegalArgumentException("action không hợp lệ");
        };
    }

    /** Các ô lineId / countedQty / lineNote gửi lặp lại theo cùng thứ tự. */
    private static List<CountedLine> countedLines(HttpServletRequest request) {
        String[] ids = request.getParameterValues("lineId");
        String[] qtys = request.getParameterValues("countedQty");
        String[] notes = request.getParameterValues("lineNote");
        List<CountedLine> lines = new ArrayList<>();
        if (ids == null) {
            return lines;
        }
        if (qtys == null || qtys.length != ids.length) {
            throw new IllegalArgumentException("Dữ liệu số lượng thực tế không khớp");
        }
        for (int i = 0; i < ids.length; i++) {
            String qty = qtys[i] == null ? "" : qtys[i].strip();
            lines.add(new CountedLine(Long.parseLong(ids[i].strip()),
                    qty.isEmpty() ? null : Integer.valueOf(qty),
                    notes != null && i < notes.length ? notes[i] : null));
        }
        return lines;
    }
}
