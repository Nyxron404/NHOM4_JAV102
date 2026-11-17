package controllers.hieulnmth08470;

import java.sql.SQLException;
import java.util.Map;

import dao.hieulnmth08470.GrindSizeDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import models.hieulnmth08470.catalog.GrindSize;

/** UC 42-45: Xem / Thêm / Sửa / Xóa độ xay (gồm cả dạng Nguyên hạt). */
@WebServlet("/api/danh-muc/do-xay")
public class GrindSizeServlet extends CrudServlet<GrindSize> {

    private final GrindSizeDAO dao = new GrindSizeDAO();

    @Override protected String label() { return "độ xay"; }

    @Override
    protected void loadList(HttpServletRequest req, Map<String, Object> data) throws SQLException {
        data.put("items", dao.search(Form.clean(req.getParameter("q"))));
        data.put("usage", dao.usageMap());
    }

    @Override
    protected GrindSize newItem() throws SQLException {
        GrindSize g = new GrindSize();
        g.setIsWholeBean(false);
        g.setSortOrder(dao.nextSortOrder());
        return g;
    }

    @Override protected GrindSize findById(int id) throws SQLException { return dao.getById(id); }

    @Override
    protected GrindSize bind(Form f, Integer id) throws SQLException {
        GrindSize g = new GrindSize();
        g.setGrindSizeId(id);
        g.setGrindCode(f.code("grindCode", "Mã độ xay"));
        g.setGrindName(f.name("grindName", "Tên độ xay"));
        g.setIsWholeBean(f.checkbox("isWholeBean"));
        Integer order = f.integer("sortOrder", "Thứ tự", false, 1, Short.MAX_VALUE);   // cột SMALLINT
        g.setSortOrder(order != null ? order : dao.nextSortOrder());
        g.setDescription(f.optionalText("description", "Mô tả", 0));
        return g;
    }

    @Override protected void insert(GrindSize g) throws SQLException { dao.insert(g); }
    @Override protected boolean update(GrindSize g) throws SQLException { return dao.update(g); }
    @Override protected boolean delete(int id) throws SQLException { return dao.delete(id); }

    @Override
    protected String usageMessage(int id) throws SQLException {
        int n = dao.countUsage(id);
        return n > 0 ? "đang được dùng bởi " + n + " gói bán (SKU)" : null;
    }
}
