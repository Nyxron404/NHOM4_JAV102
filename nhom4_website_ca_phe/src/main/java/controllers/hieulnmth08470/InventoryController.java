package controllers.hieulnmth08470;

import dao.hieulnmth08470.InventoryDAO;
import dao.hieulnmth08470.NotificationDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API tồn kho — UC105..UC116 (Lê Nguyễn Minh Hiếu). Trang: pages/hieulnmth08470/kho-hang.html
 * <pre>
 * GET  /api/kho?view=packages&keyword=&stockStatus=          UC105, UC106, UC107
 * GET  /api/kho?view=lots&expiryStatus=&from=&to=&packageId=   UC108, UC109
 * GET  /api/kho?view=lot&id=                                    UC110
 * GET  /api/kho?view=movements&lotId=&packageId=&from=&to=      UC111
 * GET  /api/kho?view=expired                                    UC112-B1
 * GET  /api/kho?view=disposals&from=&to= | view=disposal&id=    UC114
 * GET  /api/kho?view=settings                                   UC116-B1
 * POST action=dispose&lotIds=1,2&note=                          UC112-B2  (chủ cửa hàng)
 * POST action=minStock&packageId=&minStockLevel=                UC115-B2  (chủ cửa hàng)
 * POST action=nearExpiry&days=                                  UC116-B2  (chủ cửa hàng)
 * </pre>
 */
@WebServlet("/api/kho")
public class InventoryController extends InventoryBaseController {

    private final InventoryDAO inventory = new InventoryDAO();
    private final NotificationDAO notifications = new NotificationDAO();

    @Override
    protected Object handleGet(HttpServletRequest request) throws Exception {
        String view = text(request, "view");
        return switch (view == null ? "packages" : view) {
            case "packages" -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("rows", inventory.packageStock(text(request, "keyword"), text(request, "stockStatus")));
                body.put("summary", inventory.packageSummary());
                body.put("canManage", canManage(request));
                yield body;
            }
            case "lots" -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("rows", inventory.lots(text(request, "expiryStatus"), date(request, "from"),
                        date(request, "to"), optionalInt(request, "packageId"), text(request, "keyword")));
                body.put("setting", inventory.nearExpirySetting());
                body.put("canManage", canManage(request));
                yield body;
            }
            case "lot" -> found(inventory.lotDetail(requiredLong(request, "id", "mã lô")), "lô");
            case "movements" -> inventory.movements(optionalLong(request, "lotId"), optionalInt(request, "packageId"),
                    date(request, "from"), date(request, "to"));
            case "expired" -> inventory.expiredLots();
            case "disposals" -> inventory.disposals(date(request, "from"), date(request, "to"));
            case "disposal" -> inventory.disposalLines(requiredLong(request, "id", "mã phiếu xuất"));
            case "settings" -> {
                Map<String, Object> body = new LinkedHashMap<>(found(inventory.nearExpirySetting(), "cấu hình cận hạn"));
                body.put("canManage", canManage(request));
                yield body;
            }
            default -> throw new IllegalArgumentException("view không hợp lệ");
        };
    }

    @Override
    protected Object handlePost(HttpServletRequest request) throws Exception {
        String action = action(request);
        requireManager(request);
        int userId = currentUserId(request);
        return switch (action) {
            case "dispose" -> {
                Map<String, Object> result = inventory.disposeExpired(longList(request, "lotIds"),
                        text(request, "note"), userId);
                notifications.detectLowStock();   // xuất hủy làm giảm tồn -> kiểm tra tồn thấp ngay
                yield ok("Đã tạo phiếu xuất hủy #" + result.get("disposalId") + " ("
                        + result.get("lineCount") + " lô, " + result.get("quantity") + " gói)", "disposal", result);
            }
            case "minStock" -> {
                found(inventory.updateMinStock(requiredInt(request, "packageId", "gói"),
                        optionalInt(request, "minStockLevel")), "gói");
                notifications.detectLowStock();
                yield ok("Đã cập nhật ngưỡng tồn thấp");
            }
            case "nearExpiry" -> {
                found(inventory.updateNearExpiry(optionalInt(request, "days"), userId), "cấu hình cận hạn");
                notifications.detectNearExpiry();
                yield ok("Đã cập nhật số ngày cận hạn");
            }
            default -> throw new IllegalArgumentException("action không hợp lệ");
        };
    }
}
