package controllers.minhpvth08530;

import dao.minhpvth08530.LookupDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;

/**
 * API danh mục cho dropdown trang sản phẩm.
 * <pre>GET /api/product-lookups  → regions, varieties, processes, roastLevels, brewMethods, grindSizes, flavorNotes</pre>
 */
@WebServlet("/api/product-lookups")
public class LookupController extends BaseController {

    private final LookupDAO lookupDAO = new LookupDAO();

    @Override
    protected Object handleGet(HttpServletRequest request) throws Exception {
        return lookupDAO.findAll();
    }

    @Override
    protected Object handlePost(HttpServletRequest request) {
        throw new ApiException(405, "Danh mục chỉ đọc");
    }
}
