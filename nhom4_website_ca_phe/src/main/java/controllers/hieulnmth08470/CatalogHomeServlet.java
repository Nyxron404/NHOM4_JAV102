package controllers.hieulnmth08470;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Địa chỉ cũ /danh-muc (bản JSP trước đây) -> chuyển sang trang mới pages/hieulnmth08470.html.
 * Giữ lại để link cũ / bookmark không bị lỗi 404.
 */
@WebServlet(urlPatterns = {"/danh-muc", "/danh-muc/"})
public class CatalogHomeServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.sendRedirect(req.getContextPath() + "/pages/hieulnmth08470/hieulnmth08470.html");
    }
}
