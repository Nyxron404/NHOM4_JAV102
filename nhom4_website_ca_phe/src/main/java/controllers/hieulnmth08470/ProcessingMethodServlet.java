package controllers.hieulnmth08470;

import java.sql.SQLException;
import java.util.Map;

import dao.hieulnmth08470.ProcessingMethodDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import models.hieulnmth08470.catalog.ProcessingMethod;

/** UC 30-33: Xem / Thêm / Sửa / Xóa phương pháp sơ chế. */
@WebServlet("/api/danh-muc/so-che")
public class ProcessingMethodServlet extends CrudServlet<ProcessingMethod> {

    private final ProcessingMethodDAO dao = new ProcessingMethodDAO();

    @Override protected String label() { return "phương pháp sơ chế"; }

    @Override
    protected void loadList(HttpServletRequest req, Map<String, Object> data) throws SQLException {
        data.put("items", dao.search(Form.clean(req.getParameter("q"))));
        data.put("usage", dao.usageMap());
    }

    @Override protected ProcessingMethod newItem() { return new ProcessingMethod(); }
    @Override protected ProcessingMethod findById(int id) throws SQLException { return dao.getById(id); }

    @Override
    protected ProcessingMethod bind(Form f, Integer id) {
        ProcessingMethod m = new ProcessingMethod();
        m.setProcessId(id);
        m.setProcessName(f.name("processName", "Tên phương pháp"));
        m.setDescription(f.optionalText("description", "Mô tả", 0));
        return m;
    }

    @Override protected void insert(ProcessingMethod m) throws SQLException { dao.insert(m); }
    @Override protected boolean update(ProcessingMethod m) throws SQLException { return dao.update(m); }
    @Override protected boolean delete(int id) throws SQLException { return dao.delete(id); }

    @Override
    protected String usageMessage(int id) throws SQLException {
        int n = dao.countUsage(id);
        return n > 0 ? "đang được dùng bởi " + n + " biến thể sản phẩm" : null;
    }
}
