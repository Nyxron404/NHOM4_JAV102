package controllers.hieuttth08297;

import dao.hieuttth08297.KhachSanPhamDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import models.hieuttth08297.TrangSanPham;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@WebServlet("/khach-noi-bo/san-pham")
public class KhachSanPhamServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(KhachSanPhamServlet.class.getName());
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9]{8,64}");
    private final KhachSanPhamDAO dao = new KhachSanPhamDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String token = req.getParameter("token");
        token = token == null ? "" : token.strip();
        if (!TOKEN.matcher(token).matches()) {
            KhachJson.error(resp, 404, "INVALID_QR", "Mã QR không hợp lệ.");
            return;
        }
        try {
            TrangSanPham.Trang page = dao.findByToken(token);
            if (page == null) {
                KhachJson.error(resp, 404, "INVALID_QR", "Không tìm thấy sản phẩm ứng với mã QR này.");
            } else if (!page.product().active()) {
                KhachJson.error(resp, 410, "DISCONTINUED", "Sản phẩm này đã ngừng kinh doanh.");
            } else if (page.variants().isEmpty()) {
                KhachJson.error(resp, 404, "NO_VARIANT", "Sản phẩm chưa có thông tin chi tiết để hiển thị.");
            } else {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("ok", true);
                body.put("data", page);
                KhachJson.send(resp, 200, body);
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Lỗi truy vấn trang khách hàng", e);
            KhachJson.error(resp, 503, "SERVER_ERROR", "Hệ thống đang bận, vui lòng thử lại sau.");
        }
    }
}