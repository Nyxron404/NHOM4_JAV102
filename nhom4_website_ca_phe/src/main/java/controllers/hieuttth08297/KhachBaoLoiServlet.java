package controllers.hieuttth08297;

import dao.hieuttth08297.KhachBaoLoiDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/khach-noi-bo/bao-loi")
public class KhachBaoLoiServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(KhachBaoLoiServlet.class.getName());
    private static final int MAX_PER_WINDOW = 5;
    private static final long WINDOW_MS = 10 * 60 * 1000L;
    private final Map<String, Deque<Long>> recent = new ConcurrentHashMap<>();
    private final KhachBaoLoiDAO dao = new KhachBaoLoiDAO();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        if (tooMany(req.getRemoteAddr())) {
            KhachJson.error(resp, 429, "TOO_MANY", "Bạn gửi báo cáo quá nhiều lần, vui lòng thử lại sau ít phút.");
            return;
        }
        String token = cut(req.getParameter("token"), 64);
        String note = cut(req.getParameter("note"), 500);
        String agent = cut(req.getHeader("User-Agent"), 300);
        try {
            dao.insert(token, note, agent);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("message", "Cảm ơn bạn, chúng tôi đã ghi nhận báo cáo.");
            KhachJson.send(resp, 200, body);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Không lưu được báo cáo QR (đã chạy SQL tạo bảng qr_invalid_reports chưa?)", e);
            KhachJson.error(resp, 503, "SERVER_ERROR", "Chưa gửi được báo cáo, vui lòng thử lại sau.");
        }
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        s = s.strip();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private boolean tooMany(String ip) {
        long now = System.currentTimeMillis();
        Deque<Long> times = recent.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MS) times.pollFirst();
            if (times.size() >= MAX_PER_WINDOW) return true;
            times.addLast(now);
            return false;
        }
    }
}