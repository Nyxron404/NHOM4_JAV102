package controllers.minhpvth08530;

import dao.minhpvth08530.ProductDAO;
import dao.minhpvth08530.ProductVariantDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import models.enums.ActiveStatus;
import models.minhpvth08530.Product;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API sản phẩm — UC51..UC58.
 * <pre>
 * GET  /api/products?keyword=&amp;status=        danh sách / tìm kiếm / lọc (UC51, UC52, UC53)
 * GET  /api/products?id=                         chi tiết + biến thể (UC54)
 * POST /api/products action=create|update|delete|deactivate|activate  (UC55..UC58)
 * </pre>
 */
@WebServlet("/api/products")
public class ProductController extends BaseController {

    private final ProductDAO productDAO = new ProductDAO();
    private final ProductVariantDAO variantDAO = new ProductVariantDAO();

    @Override
    protected Object handleGet(HttpServletRequest request) throws Exception {
        Integer id = optionalInt(request, "id");
        if (id != null) {
            Product product = found(productDAO.findById(id), "sản phẩm");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("product", product);
            body.put("variants", variantDAO.findByProduct(id, null));
            return body;
        }
        return productDAO.findAll(text(request, "keyword"),
                enumValue(request, "status", ActiveStatus.class));
    }

    @Override
    protected Object handlePost(HttpServletRequest request) throws Exception {
        String action = action(request);
        switch (action) {
            case "create" -> {
                int id = productDAO.insert(read(request));
                return ok("Đã thêm sản phẩm", "id", id);
            }
            case "update" -> {
                Product product = read(request);
                product.setProductId(requiredInt(request, "id", "mã sản phẩm"));
                found(productDAO.update(product), "sản phẩm");
                return ok("Đã cập nhật sản phẩm");
            }
            case "delete" -> {
                found(productDAO.delete(requiredInt(request, "id", "mã sản phẩm")), "sản phẩm");
                return ok("Đã xóa sản phẩm");
            }
            case "deactivate" -> {
                found(productDAO.deactivate(requiredInt(request, "id", "mã sản phẩm")),
                        "sản phẩm đang kinh doanh");
                return ok("Đã ngừng kinh doanh sản phẩm cùng các biến thể và gói bán");
            }
            case "activate" -> {
                found(productDAO.activate(requiredInt(request, "id", "mã sản phẩm")),
                        "sản phẩm đã ngừng kinh doanh");
                return ok("Sản phẩm đã kinh doanh trở lại");
            }
            default -> throw new IllegalArgumentException("Thao tác không hợp lệ: " + action);
        }
    }

    private static Product read(HttpServletRequest request) {
        Product product = new Product();
        product.setProductCode(text(request, "productCode"));
        product.setProductName(text(request, "productName"));
        product.setDescription(text(request, "description"));
        return product;
    }
}
