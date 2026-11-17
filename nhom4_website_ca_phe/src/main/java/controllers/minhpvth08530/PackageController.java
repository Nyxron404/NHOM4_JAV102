package controllers.minhpvth08530;

import dao.minhpvth08530.CoffeePackageDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import models.enums.ActiveStatus;
import models.minhpvth08530.CoffeePackage;

/**
 * API gói bán và giá bán — UC74..UC80.
 * <pre>
 * GET  /api/packages?variantId=&amp;productId=&amp;status=   danh sách gói bán (UC74)
 * GET  /api/packages?historyOf=                          lịch sử giá (UC79)
 * POST /api/packages action=
 *      create (UC75) | update (UC76) | delete (UC77)
 *      price salePrice=&amp;effectiveFrom=&amp;note= (UC78)
 *      deactivate (UC80) | activate
 * </pre>
 */
@WebServlet("/api/packages")
public class PackageController extends BaseController {

    private final CoffeePackageDAO packageDAO = new CoffeePackageDAO();

    @Override
    protected Object handleGet(HttpServletRequest request) throws Exception {
        Integer historyOf = optionalInt(request, "historyOf");
        if (historyOf != null) {
            found(packageDAO.findById(historyOf), "gói bán");
            return packageDAO.findPriceHistory(historyOf);
        }
        Integer id = optionalInt(request, "id");
        if (id != null) {
            return found(packageDAO.findById(id), "gói bán");
        }
        return packageDAO.findAll(optionalInt(request, "variantId"),
                optionalInt(request, "productId"), enumValue(request, "status", ActiveStatus.class));
    }

    @Override
    protected Object handlePost(HttpServletRequest request) throws Exception {
        String action = action(request);
        if ("create".equals(action)) {
            CoffeePackage pkg = read(request);
            pkg.setVariantId(requiredInt(request, "variantId", "biến thể"));
            int id = packageDAO.insert(pkg, money(request, "salePrice", "Giá bán"),
                    currentUserId(request));
            return ok("Đã thêm gói bán", "id", id);
        }

        int id = requiredInt(request, "id", "mã gói bán");
        switch (action) {
            case "update" -> {
                CoffeePackage pkg = read(request);
                pkg.setPackageId(id);
                found(packageDAO.update(pkg), "gói bán");
                return ok("Đã cập nhật gói bán");
            }
            case "delete" -> {
                found(packageDAO.delete(id), "gói bán");
                return ok("Đã xóa gói bán");
            }
            case "price" -> {
                var effectiveFrom = dateTime(request, "effectiveFrom");
                packageDAO.updatePrice(id, money(request, "salePrice", "Giá bán"), effectiveFrom,
                        currentUserId(request), text(request, "note"));
                return ok(effectiveFrom == null ? "Đã cập nhật giá bán"
                        : "Đã hẹn giá mới, tự áp dụng khi đến ngày");
            }
            case "deactivate" -> {
                found(packageDAO.deactivate(id), "gói bán đang bán");
                return ok("Đã ngừng bán gói");
            }
            case "activate" -> {
                packageDAO.activate(id);
                return ok("Đã mở bán lại gói");
            }
            default -> throw new IllegalArgumentException("Thao tác không hợp lệ: " + action);
        }
    }

    private static CoffeePackage read(HttpServletRequest request) {
        CoffeePackage pkg = new CoffeePackage();
        pkg.setSku(text(request, "sku"));
        pkg.setGrindSizeId(optionalInt(request, "grindSizeId"));
        pkg.setWeightG(optionalInt(request, "weightG"));
        pkg.setMinStockLevel(optionalInt(request, "minStockLevel"));
        return pkg;
    }
}
