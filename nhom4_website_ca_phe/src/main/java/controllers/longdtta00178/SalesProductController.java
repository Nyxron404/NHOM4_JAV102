package controllers.longdtta00178;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import dao.longdtta00178.SalesProductDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import models.longdtta00178.SalesProductCard;

/**
 * API cho cột trái màn bán hàng (chỉ có GET, vì màn này chỉ ĐỌC dữ liệu).
 *
 * Trình duyệt / JS gọi:
 *   GET /api/ban-hang/san-pham?action=filters
 *       -> {"species":[...],"regions":[...],"roastLevels":[...]}   (đổ ô lọc)
 *   GET /api/ban-hang/san-pham?q=culi&species=Robusta&form=WB&regionId=3&roastLevelId=2&inStock=true
 *       -> [ {thẻ cà phê}, ... ]    mọi tham số đều không bắt buộc, bỏ trống = không lọc
 *
 * Cách hoạt động của doGet:
 *   1. Tomcat thấy URL khớp @WebServlet bên dưới -> gọi doGet(req, resp)
 *   2. req.getParameter("q") đọc giá trị sau dấu ? trên URL (không có thì trả null)
 *   3. Gọi DAO chạy SQL -> nhận List<SalesProductCard>
 *   4. SalesJson đổi List thành chuỗi JSON -> resp.getWriter().write(...) gửi về trình duyệt
 *   5. Lỗi thì gửi {"error":"..."} kèm mã 400 (sai tham số) hoặc 500 (lỗi DB)
 */
@WebServlet("/api/ban-hang/san-pham")
public class SalesProductController extends HttpServlet {

    private final SalesProductDAO dao = new SalesProductDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Báo cho trình duyệt: nội dung trả về là JSON, mã UTF-8 (để không lỗi tiếng Việt)
        resp.setContentType("application/json;charset=UTF-8");

        try {
            // ---- 1. Danh mục cho ô lọc ----
            if ("filters".equals(req.getParameter("action"))) {
                Map<String, List<Map<String, Object>>> options = dao.findFilterOptions();
                resp.getWriter().write(SalesJson.filters(options));
                return;
            }

            // ---- 2. Danh sách thẻ cà phê ----
            // Đọc tham số trên URL
            String keyword = emptyToNull(req.getParameter("q"));
            String species = emptyToNull(req.getParameter("species"));
            String form = req.getParameter("form");                // "WB" | "GROUND" | null
            Integer regionId = toInt(req.getParameter("regionId"));
            Integer roastLevelId = toInt(req.getParameter("roastLevelId"));
            boolean inStockOnly = "true".equals(req.getParameter("inStock"));

            // form -> true (nguyên hạt) / false (xay sẵn) / null (tất cả)
            Boolean wholeBean = null;
            if ("WB".equals(form)) wholeBean = true;
            if ("GROUND".equals(form)) wholeBean = false;

            // Gọi DAO lấy dữ liệu
            List<SalesProductCard> cards = dao.findSellableCards(
                    keyword, species, wholeBean, regionId, roastLevelId, inStockOnly);

            // Đổi sang JSON và gửi về
            resp.getWriter().write(SalesJson.cards(cards));

        } catch (NumberFormatException e) {               // regionId=abc ...
            resp.setStatus(400);
            resp.getWriter().write(SalesJson.error("Tham số lọc không hợp lệ"));
        } catch (Exception e) {                            // lỗi kết nối / SQL
            e.printStackTrace();                           // xem chi tiết trong log Tomcat
            resp.setStatus(500);
            resp.getWriter().write(SalesJson.error("Lỗi tải sản phẩm: " + e.getMessage()));
        }
    }

    /** "3" -> 3, "" hoặc null -> null */
    private static Integer toInt(String value) {
        return value == null || value.isBlank() ? null : Integer.valueOf(value.trim());
    }

    /** "" hoặc chỉ có dấu cách -> null (để SQL hiểu là không lọc) */
    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
