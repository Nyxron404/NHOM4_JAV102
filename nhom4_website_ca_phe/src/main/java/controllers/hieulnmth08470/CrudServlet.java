package controllers.hieulnmth08470;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Khung API JSON dùng chung cho 1 danh mục. Trang pages/hieulnmth08470.html gọi bằng fetch().
 *
 * <pre>
 * GET  /api/danh-muc/xxx?q=...            -> 200 {"items":[...], "usage":{id:số}, ...dữ liệu cho ô chọn}
 * GET  /api/danh-muc/xxx?action=new        -> 200 {"item":{giá trị mặc định}}
 * GET  /api/danh-muc/xxx?id=5              -> 200 {"item":{...}}  | 404 {"ok":false,"message":...}
 * POST /api/danh-muc/xxx  action=save      -> 200 {"ok":true,"message":...}
 *                                           | 400 {"ok":false,"errors":{"tenO":"lỗi"}}  (lỗi nhập / trùng)
 * POST /api/danh-muc/xxx  action=delete&id -> 200 {"ok":true,...} | 409 {"ok":false,"message":"đang được dùng..."}
 * </pre>
 * Dữ liệu POST gửi dạng form (application/x-www-form-urlencoded) nên vẫn đọc bằng req.getParameter().
 *
 * @param <T> model của danh mục (GrowingRegion, Supplier...)
 */
public abstract class CrudServlet<T> extends HttpServlet {

    /**
     * Tên ràng buộc UNIQUE trong schema -> {ô bị lỗi, thông báo}.
     * Tên index ux_* lấy từ file schema; *_key là tên PostgreSQL tự đặt cho cột UNIQUE.
     */
    private static final Map<String, String[]> UNIQUE_MESSAGES = Map.ofEntries(
            Map.entry("ux_growing_regions_name_ci", new String[] {"regionName", "Tỉnh này đã có vùng trồng cùng tên"}),
            Map.entry("ux_coffee_species_species_name_ci", new String[] {"speciesName", "Tên loài đã tồn tại"}),
            Map.entry("ux_coffee_varieties_variety_name_ci", new String[] {"varietyName", "Tên giống đã tồn tại"}),
            Map.entry("ux_processing_methods_process_name_ci", new String[] {"processName", "Tên phương pháp sơ chế đã tồn tại"}),
            Map.entry("ux_roast_levels_roast_name_ci", new String[] {"roastName", "Tên mức rang đã tồn tại"}),
            Map.entry("roast_levels_sort_order_key", new String[] {"sortOrder", "Thứ tự này đã được dùng cho mức rang khác"}),
            Map.entry("ux_brew_methods_method_name_ci", new String[] {"methodName", "Tên phương pháp pha đã tồn tại"}),
            Map.entry("ux_grind_sizes_grind_name_ci", new String[] {"grindName", "Tên độ xay đã tồn tại"}),
            Map.entry("grind_sizes_grind_code_key", new String[] {"grindCode", "Mã độ xay đã tồn tại"}),
            Map.entry("grind_sizes_sort_order_key", new String[] {"sortOrder", "Thứ tự này đã được dùng cho độ xay khác"}),
            Map.entry("suppliers_supplier_code_key", new String[] {"supplierCode", "Mã nhà cung cấp đã tồn tại"}),
            Map.entry("suppliers_tax_code_key", new String[] {"taxCode", "Mã số thuế đã thuộc về nhà cung cấp khác"}));

    // ------------------------------------------------------------ phần lớp con phải viết

    /** Tên hiển thị trong thông báo, VD "vùng trồng". */
    protected abstract String label();

    /** Đọc danh sách theo ô tìm kiếm/bộ lọc, ghi vào data ("items", "usage", danh sách cho ô chọn...). */
    protected abstract void loadList(HttpServletRequest req, Map<String, Object> data) throws SQLException;

    /** Bản ghi mới với giá trị mặc định (VD thứ tự kế tiếp). */
    protected abstract T newItem() throws SQLException;

    protected abstract T findById(int id) throws SQLException;

    /** Đọc form thành model; lỗi nhập ghi vào form.error(...). id = null khi thêm mới. */
    protected abstract T bind(Form form, Integer id) throws SQLException;

    protected abstract void insert(T item) throws SQLException;

    /** @return false nếu bản ghi không còn tồn tại */
    protected abstract boolean update(T item) throws SQLException;

    /** Trả về lý do không xóa được (đang được dùng), hoặc null nếu xóa được. */
    protected abstract String usageMessage(int id) throws SQLException;

    /** @return false nếu bản ghi không còn tồn tại */
    protected abstract boolean delete(int id) throws SQLException;

    /** Lớp con xử lý thêm action GET riêng (VD dữ liệu trang gán độ xay). Trả false nếu không nhận. */
    protected boolean handleExtraGet(String action, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, SQLException {
        return false;
    }

    /** Lớp con xử lý thêm action POST riêng (VD đổi trạng thái NCC). Trả false nếu không nhận. */
    protected boolean handleExtraPost(String action, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, SQLException {
        return false;
    }

    // ------------------------------------------------------------ xử lý chung

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");      // đọc đúng tiếng Việt từ form
        resp.setCharacterEncoding("UTF-8");
        super.service(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String action = req.getParameter("action");
            if (action != null && handleExtraGet(action, req, resp)) {
                return;
            }
            if ("new".equals(action)) {
                Json.send(resp, 200, Map.of("item", newItem()));
                return;
            }
            if (req.getParameter("id") != null) {
                Integer id = Form.parseId(req.getParameter("id"));
                T item = id == null ? null : findById(id);
                if (item == null) {
                    fail(resp, 404, "Không tìm thấy " + label() + " (có thể đã bị xóa)");
                } else {
                    Json.send(resp, 200, Map.of("item", item));
                }
                return;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            loadList(req, data);
            Json.send(resp, 200, data);
        } catch (SQLException e) {
            dbFail(resp, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String action = req.getParameter("action");
        try {
            if ("save".equals(action)) {
                save(req, resp);
            } else if ("delete".equals(action)) {
                remove(req, resp);
            } else if (!handleExtraPost(action, req, resp)) {
                fail(resp, 400, "Thao tác không hợp lệ");
            }
        } catch (SQLException e) {
            dbFail(resp, e);
        }
    }

    private void save(HttpServletRequest req, HttpServletResponse resp) throws IOException, SQLException {
        Integer id = Form.parseId(req.getParameter("id"));
        Form form = new Form(req);
        T item = bind(form, id);

        if (!form.hasErrors()) {
            try {
                if (id == null) {
                    insert(item);
                    ok(resp, "Đã thêm " + label() + " mới");
                } else if (update(item)) {
                    ok(resp, "Đã cập nhật " + label());
                } else {
                    fail(resp, 404, "Không tìm thấy " + label() + " (có thể đã bị xóa)");
                }
                return;
            } catch (SQLException e) {
                applyDbError(form, e);
            }
        }
        // Lỗi nhập hoặc trùng dữ liệu: trả về từng ô bị lỗi để JS tô đỏ
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("errors", form.getErrors());
        Json.send(resp, 400, body);
    }

    private void remove(HttpServletRequest req, HttpServletResponse resp) throws IOException, SQLException {
        Integer id = Form.parseId(req.getParameter("id"));
        if (id == null) {
            fail(resp, 400, "Thiếu mã " + label() + " cần xóa");
            return;
        }
        String reason = usageMessage(id);
        if (reason != null) {
            fail(resp, 409, "Không thể xóa " + label() + ": " + reason);
            return;
        }
        try {
            if (delete(id)) {
                ok(resp, "Đã xóa " + label());
            } else {
                fail(resp, 404, "Không tìm thấy " + label() + " (có thể đã bị xóa)");
            }
        } catch (SQLException e) {
            // Có người khác vừa dùng tới bản ghi này giữa lúc kiểm tra và lúc xóa
            if ("23503".equals(e.getSQLState())) {
                fail(resp, 409, "Không thể xóa " + label() + " vì đang được sử dụng");
            } else {
                throw e;
            }
        }
    }

    // ------------------------------------------------------------ tiện ích trả kết quả

    protected static void ok(HttpServletResponse resp, String message) throws IOException {
        Json.send(resp, 200, Map.of("ok", true, "message", message));
    }

    protected static void fail(HttpServletResponse resp, int status, String message) throws IOException {
        Json.send(resp, status, Map.of("ok", false, "message", message));
    }

    private static void dbFail(HttpServletResponse resp, SQLException e) throws IOException {
        e.printStackTrace();
        fail(resp, 500, dbMessage(e));
    }

    /** Gắn lỗi trùng dữ liệu vào đúng ô; lỗi khác thành lỗi chung. */
    protected void applyDbError(Form form, SQLException e) {
        if ("23505".equals(e.getSQLState())) {
            String[] mapped = UNIQUE_MESSAGES.get(constraintName(e));
            if (mapped != null) {
                form.error(mapped[0], mapped[1]);
                return;
            }
        }
        e.printStackTrace();
        form.error(Form.GLOBAL, dbMessage(e));
    }

    protected static String dbMessage(SQLException e) {
        String state = e.getSQLState() == null ? "" : e.getSQLState();
        if (state.startsWith("08")) return "Không kết nối được cơ sở dữ liệu, vui lòng thử lại";
        return switch (state) {
            case "23505" -> "Dữ liệu bị trùng với bản ghi đã có";
            case "23503" -> "Dữ liệu đang được sử dụng hoặc tham chiếu tới bản ghi không tồn tại";
            case "23514", "23502", "22001" -> "Dữ liệu không hợp lệ: " + e.getMessage();
            default -> "Lỗi cơ sở dữ liệu: " + e.getMessage();
        };
    }

    private static String constraintName(SQLException e) {
        if (e instanceof PSQLException pe) {
            ServerErrorMessage m = pe.getServerErrorMessage();
            if (m != null) return m.getConstraint();
        }
        return null;
    }
}
