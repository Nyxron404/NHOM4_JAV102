package controllers.hieulnmth08470;

import java.sql.SQLException;
import java.util.Map;

import dao.hieulnmth08470.CoffeeSpeciesDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import models.hieulnmth08470.catalog.CoffeeSpecies;

/** UC 22-25: Xem / Thêm / Sửa / Xóa loài cà phê. */
@WebServlet("/api/danh-muc/loai-ca-phe")
public class CoffeeSpeciesServlet extends CrudServlet<CoffeeSpecies> {

    private final CoffeeSpeciesDAO dao = new CoffeeSpeciesDAO();

    @Override protected String label() { return "loài cà phê"; }

    @Override
    protected void loadList(HttpServletRequest req, Map<String, Object> data) throws SQLException {
        data.put("items", dao.search(Form.clean(req.getParameter("q"))));
        data.put("usage", dao.usageMap());
    }

    @Override protected CoffeeSpecies newItem() { return new CoffeeSpecies(); }
    @Override protected CoffeeSpecies findById(int id) throws SQLException { return dao.getById(id); }

    @Override
    protected CoffeeSpecies bind(Form f, Integer id) {
        CoffeeSpecies s = new CoffeeSpecies();
        s.setSpeciesId(id);
        s.setSpeciesName(f.name("speciesName", "Tên loài"));
        s.setDescription(f.optionalText("description", "Mô tả", 0));
        return s;
    }

    @Override protected void insert(CoffeeSpecies s) throws SQLException { dao.insert(s); }
    @Override protected boolean update(CoffeeSpecies s) throws SQLException { return dao.update(s); }
    @Override protected boolean delete(int id) throws SQLException { return dao.delete(id); }

    @Override
    protected String usageMessage(int id) throws SQLException {
        int n = dao.countUsage(id);
        return n > 0 ? "còn " + n + " giống cà phê thuộc loài này, hãy xóa hoặc chuyển các giống đó trước" : null;
    }
}
