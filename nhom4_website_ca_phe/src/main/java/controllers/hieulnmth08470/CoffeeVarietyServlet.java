package controllers.hieulnmth08470;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dao.hieulnmth08470.CoffeeSpeciesDAO;
import dao.hieulnmth08470.CoffeeVarietyDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import models.hieulnmth08470.catalog.CoffeeSpecies;
import models.hieulnmth08470.catalog.CoffeeVariety;

/** UC 26-29: Xem / Thêm / Sửa / Xóa giống cà phê (mỗi giống thuộc 1 loài). */
@WebServlet("/api/danh-muc/giong-ca-phe")
public class CoffeeVarietyServlet extends CrudServlet<CoffeeVariety> {

    private final CoffeeVarietyDAO dao = new CoffeeVarietyDAO();
    private final CoffeeSpeciesDAO speciesDao = new CoffeeSpeciesDAO();

    @Override protected String label() { return "giống cà phê"; }

    @Override
    protected void loadList(HttpServletRequest req, Map<String, Object> data) throws SQLException {
        Integer speciesId = Form.parseId(req.getParameter("speciesId"));
        data.put("items", dao.search(Form.clean(req.getParameter("q")), speciesId));
        data.put("usage", dao.usageMap());
        data.put("speciesId", speciesId);

        List<CoffeeSpecies> species = speciesDao.getAll();
        Map<Integer, String> speciesNames = new LinkedHashMap<>();
        for (CoffeeSpecies s : species) speciesNames.put(s.getSpeciesId(), s.getSpeciesName());
        data.put("speciesList", species);
        data.put("speciesNames", speciesNames);
    }

    @Override protected CoffeeVariety newItem() { return new CoffeeVariety(); }
    @Override protected CoffeeVariety findById(int id) throws SQLException { return dao.getById(id); }

    @Override
    protected CoffeeVariety bind(Form f, Integer id) {
        CoffeeVariety v = new CoffeeVariety();
        v.setVarietyId(id);
        v.setSpeciesId(f.select("speciesId", "loài cà phê"));
        v.setVarietyName(f.name("varietyName", "Tên giống"));
        v.setDescription(f.optionalText("description", "Mô tả", 0));
        return v;
    }

    @Override protected void insert(CoffeeVariety v) throws SQLException { dao.insert(v); }
    @Override protected boolean update(CoffeeVariety v) throws SQLException { return dao.update(v); }
    @Override protected boolean delete(int id) throws SQLException { return dao.delete(id); }

    @Override
    protected String usageMessage(int id) throws SQLException {
        int n = dao.countUsage(id);
        return n > 0 ? "đang được dùng bởi " + n + " biến thể sản phẩm" : null;
    }
}
