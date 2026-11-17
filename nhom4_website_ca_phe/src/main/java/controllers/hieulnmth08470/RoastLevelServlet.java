package controllers.hieulnmth08470;

import java.sql.SQLException;
import java.util.Map;

import dao.hieulnmth08470.RoastLevelDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import models.hieulnmth08470.catalog.RoastLevel;

/** UC 34-37: Xem / Thêm / Sửa / Xóa mức rang. */
@WebServlet("/api/danh-muc/muc-rang")
public class RoastLevelServlet extends CrudServlet<RoastLevel> {

    private final RoastLevelDAO dao = new RoastLevelDAO();

    @Override protected String label() { return "mức rang"; }

    @Override
    protected void loadList(HttpServletRequest req, Map<String, Object> data) throws SQLException {
        data.put("items", dao.search(Form.clean(req.getParameter("q"))));
        data.put("usage", dao.usageMap());
    }

    /** Form thêm mới gợi ý sẵn thứ tự kế tiếp. */
    @Override
    protected RoastLevel newItem() throws SQLException {
        RoastLevel r = new RoastLevel();
        r.setSortOrder(dao.nextSortOrder());
        return r;
    }

    @Override protected RoastLevel findById(int id) throws SQLException { return dao.getById(id); }

    @Override
    protected RoastLevel bind(Form f, Integer id) throws SQLException {
        RoastLevel r = new RoastLevel();
        r.setRoastLevelId(id);
        r.setRoastName(f.name("roastName", "Tên mức rang"));
        Integer order = f.integer("sortOrder", "Thứ tự", false, 1, Short.MAX_VALUE);   // cột SMALLINT
        r.setSortOrder(order != null ? order : dao.nextSortOrder());
        r.setDescription(f.optionalText("description", "Mô tả", 0));
        return r;
    }

    @Override protected void insert(RoastLevel r) throws SQLException { dao.insert(r); }
    @Override protected boolean update(RoastLevel r) throws SQLException { return dao.update(r); }
    @Override protected boolean delete(int id) throws SQLException { return dao.delete(id); }

    @Override
    protected String usageMessage(int id) throws SQLException {
        int n = dao.countUsage(id);
        return n > 0 ? "đang được dùng bởi " + n + " biến thể sản phẩm" : null;
    }
}
