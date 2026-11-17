package controllers.hieulnmth08470;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dao.hieulnmth08470.BrewMethodDAO;
import dao.hieulnmth08470.BrewMethodGrindSizeDAO;
import dao.hieulnmth08470.GrindSizeDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import models.hieulnmth08470.catalog.BrewMethod;
import models.hieulnmth08470.catalog.BrewMethodGrindSize;
import models.hieulnmth08470.catalog.GrindSize;

/**
 * UC 38-41: Xem / Thêm / Sửa / Xóa phương pháp pha.
 * UC 46: Gán độ xay phù hợp cho phương pháp pha
 *   GET  ?action=grind&amp;id=5   -> dữ liệu cho màn hình gán độ xay
 *   POST action=saveGrind       -> lưu (mỗi độ xay: không dùng / phù hợp / khuyến nghị)
 */
@WebServlet("/api/danh-muc/phuong-phap-pha")
public class BrewMethodServlet extends CrudServlet<BrewMethod> {

    /** Giá trị của ô chọn trên trang gán độ xay. */
    private static final String SUITABLE = "OK";
    private static final String RECOMMENDED = "REC";

    private final BrewMethodDAO dao = new BrewMethodDAO();
    private final GrindSizeDAO grindDao = new GrindSizeDAO();
    private final BrewMethodGrindSizeDAO assignDao = new BrewMethodGrindSizeDAO();

    @Override protected String label() { return "phương pháp pha"; }

    @Override
    protected void loadList(HttpServletRequest req, Map<String, Object> data) throws SQLException {
        data.put("items", dao.search(Form.clean(req.getParameter("q"))));
        data.put("usage", dao.usageMap());
        data.put("grindNames", assignDao.grindNamesByBrewMethod());
    }

    @Override protected BrewMethod newItem() { return new BrewMethod(); }
    @Override protected BrewMethod findById(int id) throws SQLException { return dao.getById(id); }

    @Override
    protected BrewMethod bind(Form f, Integer id) {
        BrewMethod m = new BrewMethod();
        m.setBrewMethodId(id);
        m.setMethodName(f.name("methodName", "Tên phương pháp"));
        m.setCoffeeWaterRatio(f.optionalText("coffeeWaterRatio", "Tỉ lệ cà phê : nước", 20));
        m.setWaterTempC(f.optionalText("waterTempC", "Nhiệt độ nước", 20));
        m.setBrewTime(f.optionalText("brewTime", "Thời gian pha", 30));
        m.setGuide(f.optionalText("guide", "Hướng dẫn", 0));
        return m;
    }

    @Override protected void insert(BrewMethod m) throws SQLException { dao.insert(m); }
    @Override protected boolean update(BrewMethod m) throws SQLException { return dao.update(m); }
    @Override protected boolean delete(int id) throws SQLException { return dao.delete(id); }

    @Override
    protected String usageMessage(int id) throws SQLException {
        int n = dao.countUsage(id);
        return n > 0 ? "đang được gợi ý cho " + n + " biến thể sản phẩm" : null;
    }

    // ------------------------------------------------------------ UC 46: gán độ xay

    /** GET ?action=grind&id=5 -> {"method":{...}, "grindSizes":[...], "selected":{grindSizeId:"OK"|"REC"}} */
    @Override
    protected boolean handleExtraGet(String action, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, SQLException {
        if (!"grind".equals(action)) return false;
        BrewMethod method = findMethod(req, resp);
        if (method == null) return true;

        Map<Integer, String> selected = new HashMap<>();   // grind_size_id -> OK / REC
        for (BrewMethodGrindSize a : assignDao.getByBrewMethod(method.getBrewMethodId())) {
            selected.put(a.getGrindSizeId(), Boolean.TRUE.equals(a.getIsRecommended()) ? RECOMMENDED : SUITABLE);
        }
        List<GrindSize> grindSizes = new ArrayList<>();
        for (GrindSize g : grindDao.getAll()) {
            if (!Boolean.TRUE.equals(g.getIsWholeBean())) grindSizes.add(g);   // nguyên hạt không dùng để pha
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("method", method);
        data.put("grindSizes", grindSizes);
        data.put("selected", selected);
        Json.send(resp, 200, data);
        return true;
    }

    /** POST action=saveGrind&id=5&grind_3=REC&grind_4=OK ... (bỏ trống = không dùng) */
    @Override
    protected boolean handleExtraPost(String action, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, SQLException {
        if (!"saveGrind".equals(action)) return false;
        BrewMethod method = findMethod(req, resp);
        if (method == null) return true;

        List<BrewMethodGrindSize> items = new ArrayList<>();
        int recommended = 0;
        for (GrindSize g : grindDao.getAll()) {
            if (Boolean.TRUE.equals(g.getIsWholeBean())) continue;
            String choice = req.getParameter("grind_" + g.getGrindSizeId());
            if (SUITABLE.equals(choice) || RECOMMENDED.equals(choice)) {
                BrewMethodGrindSize a = new BrewMethodGrindSize();
                a.setBrewMethodId(method.getBrewMethodId());
                a.setGrindSizeId(g.getGrindSizeId());
                a.setIsRecommended(RECOMMENDED.equals(choice));
                items.add(a);
                if (a.getIsRecommended()) recommended++;
            }
        }
        assignDao.replaceAll(method.getBrewMethodId(), items);

        ok(resp, items.isEmpty()
                ? "Đã bỏ toàn bộ độ xay của \"" + method.getMethodName() + "\""
                : "Đã gán " + items.size() + " độ xay (" + recommended + " khuyến nghị) cho \""
                        + method.getMethodName() + "\"");
        return true;
    }

    private BrewMethod findMethod(HttpServletRequest req, HttpServletResponse resp) throws IOException, SQLException {
        Integer id = Form.parseId(req.getParameter("id"));
        BrewMethod method = id == null ? null : dao.getById(id);
        if (method == null) {
            fail(resp, 404, "Không tìm thấy phương pháp pha");
        }
        return method;
    }
}
