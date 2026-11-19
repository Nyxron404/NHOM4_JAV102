package controllers.minhpvth08530;

import dao.minhpvth08530.LookupDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lớp cha của các controller phần sản phẩm: trả JSON UTF-8, đọc tham số an toàn
 * và đổi lỗi nghiệp vụ / lỗi database thành thông báo tiếng Việt cho trang web.
 */
public abstract class BaseController extends HttpServlet {

    protected static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Xử lý GET, trả về đối tượng sẽ được chuyển thành JSON. */
    protected abstract Object handleGet(HttpServletRequest request) throws Exception;

    /** Xử lý POST theo tham số action, trả về đối tượng sẽ được chuyển thành JSON. */
    protected abstract Object handlePost(HttpServletRequest request) throws Exception;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handle(request, response, true);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handle(request, response, false);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response, boolean get)
            throws IOException {
        request.setCharacterEncoding("UTF-8");
        int status = 200;
        Object body;
        try {
            body = get ? handleGet(request) : handlePost(request);
        } catch (ApiException exception) {
            status = exception.getStatus();
            body = error(exception.getMessage());
        } catch (NumberFormatException exception) {
            status = 400;
            body = error("Giá trị số không hợp lệ");
        } catch (IllegalArgumentException exception) {
            status = 400;
            body = error(exception.getMessage());
        } catch (IllegalStateException exception) {
            status = 409;
            body = error(exception.getMessage());
        } catch (SQLException exception) {
            status = sqlStatus(exception);
            body = error(sqlMessage(exception));
            if (status >= 500) {
                // 500 lỗi truy vấn, 503 không kết nối được: ghi log để xem nguyên nhân thật
                log("Lỗi database (SQLState " + exception.getSQLState() + ")", exception);
            }
        } catch (Exception exception) {
            status = 500;
            body = error("Lỗi hệ thống, vui lòng thử lại");
            log("Lỗi không mong muốn", exception);
        }
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter writer = response.getWriter();
        writer.write(Json.stringify(body));
        writer.flush();
    }

    // ------------------------------------------------------------------ đọc tham số

    protected static String action(HttpServletRequest request) {
        String action = request.getParameter("action");
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Thiếu tham số action");
        }
        return action.strip();
    }

    protected static String text(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null || value.isBlank() ? null : value;
    }

    protected static Integer optionalInt(HttpServletRequest request, String name) {
        String value = text(request, name);
        return value == null ? null : Integer.valueOf(value.strip());
    }

    protected static int requiredInt(HttpServletRequest request, String name, String label) {
        Integer value = optionalInt(request, name);
        if (value == null) {
            throw new IllegalArgumentException("Thiếu " + label);
        }
        return value;
    }

    protected static BigDecimal money(HttpServletRequest request, String name, String label) {
        String value = text(request, name);
        if (value == null) {
            throw new IllegalArgumentException(label + " không được để trống");
        }
        String number = value.replace(" ", "").strip();
        if (number.matches("\\d{1,3}([.,]\\d{3})+")) {
            number = number.replace(".", "").replace(",", ""); // "255.000" -> 255000
        }
        return new BigDecimal(number);
    }

    /** Ô datetime-local ("2026-11-20T08:00") hiểu theo giờ Việt Nam; rỗng = null. */
    protected static OffsetDateTime dateTime(HttpServletRequest request, String name) {
        String value = text(request, name);
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.strip()).atZone(VIETNAM).toOffsetDateTime();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Ngày giờ không hợp lệ");
        }
    }

    protected static <E extends Enum<E>> E enumValue(HttpServletRequest request, String name,
                                                     Class<E> type) {
        String value = text(request, name);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ: " + value);
        }
    }

    /**
     * Người thao tác: lấy user_id trong session (attribute "userId", do phần đăng nhập ghi vào).
     * Khi phần đăng nhập chưa xong thì dùng tài khoản ACTIVE đầu tiên để vẫn ghi được lịch sử giá.
     */
    protected static int currentUserId(HttpServletRequest request) throws SQLException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object value = session.getAttribute("userId");
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                return Integer.parseInt(text.strip());
            }
        }
        Integer fallback = new LookupDAO().findDefaultUserId();
        if (fallback == null) {
            throw new ApiException(401, "Chưa có tài khoản người dùng nào, hãy đăng nhập");
        }
        return fallback;
    }

    protected static <T> T found(T value, String label) {
        if (value == null || Boolean.FALSE.equals(value)) {
            throw new ApiException(404, "Không tìm thấy " + label);
        }
        return value;
    }

    protected static Map<String, Object> ok(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("message", message);
        return body;
    }

    protected static Map<String, Object> ok(String message, String key, Object value) {
        Map<String, Object> body = ok(message);
        body.put(key, value);
        return body;
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("error", message);
        return body;
    }

    // ------------------------------------------------------------------ lỗi database

    private static final String[][] CONSTRAINT_MESSAGES = {
        {"products_product_code_key", "Mã sản phẩm đã tồn tại"},
        {"product_variants_variant_code_key", "Mã biến thể đã tồn tại"},
        {"packages_sku_key", "SKU đã tồn tại"},
        {"packages_variant_id_weight_g_grind_size_id_key",
            "Biến thể đã có gói cùng trọng lượng và dạng hạt này"},
        {"package_price_histories_package_id_effective_from_key",
            "Đã có mốc giá trùng thời điểm áp dụng"},
        {"variant_images_public_id_key", "Ảnh này đã được lưu"},
        {"users_username_key", "Tên đăng nhập đã tồn tại"},
        {"users_email_key", "Email đã được tài khoản khác sử dụng"},
        {"_fkey", "Dữ liệu liên quan không tồn tại hoặc đang được sử dụng"},
    };

    private static int sqlStatus(SQLException exception) {
        String state = exception.getSQLState();
        if (state == null) {
            return 500;
        }
        return switch (state) {
            case "23505", "23503" -> 409;
            case "23514", "23502", "22001", "22003", "22P02" -> 400;
            case "08001", "08006", "08004", "28P01", "3D000" -> 503;
            default -> 500;
        };
    }

    private static String sqlMessage(SQLException exception) {
        String text = String.valueOf(exception.getMessage());
        for (String[] pair : CONSTRAINT_MESSAGES) {
            if (text.contains(pair[0])) {
                return pair[1];
            }
        }
        String state = String.valueOf(exception.getSQLState());
        return switch (state) {
            case "23514", "23502" -> "Dữ liệu không thỏa ràng buộc của hệ thống";
            case "22001" -> "Dữ liệu nhập quá dài";
            case "22003" -> "Giá trị số quá lớn";
            case "08001", "08006", "08004", "28P01", "3D000" -> "Không kết nối được cơ sở dữ liệu";
            default -> "Lỗi cơ sở dữ liệu";
        };
    }
}
