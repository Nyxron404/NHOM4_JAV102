package controllers.hieulnmth08470;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dao.hieulnmth08470.GrowingRegionDAO;
import dao.hieulnmth08470.ProvinceDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import models.hieulnmth08470.catalog.GrowingRegion;
import models.hieulnmth08470.catalog.Province;

/** UC 18-21: Xem / Thêm / Sửa / Xóa vùng trồng. */
@WebServlet("/api/danh-muc/vung-trong")
public class GrowingRegionServlet extends CrudServlet<GrowingRegion> {

    private final GrowingRegionDAO dao = new GrowingRegionDAO();
    private final ProvinceDAO provinceDao = new ProvinceDAO();

    @Override protected String label() { return "vùng trồng"; }

    @Override
    protected void loadList(HttpServletRequest req, Map<String, Object> data) throws SQLException {
        Integer provinceId = Form.parseId(req.getParameter("provinceId"));
        data.put("items", dao.search(Form.clean(req.getParameter("q")), provinceId));
        data.put("usage", dao.usageMap());
        data.put("provinceId", provinceId);

        List<Province> provinces = provinceDao.getAll();
        Map<Integer, String> provinceNames = new LinkedHashMap<>();
        for (Province p : provinces) provinceNames.put(p.getProvinceId(), p.getProvinceName());
        data.put("provinces", provinces);
        data.put("provinceNames", provinceNames);
    }

    @Override protected GrowingRegion newItem() { return new GrowingRegion(); }
    @Override protected GrowingRegion findById(int id) throws SQLException { return dao.getById(id); }

    @Override
    protected GrowingRegion bind(Form f, Integer id) {
        GrowingRegion r = new GrowingRegion();
        r.setRegionId(id);
        r.setProvinceId(f.select("provinceId", "tỉnh/thành"));
        r.setRegionName(f.name("regionName", "Tên vùng trồng"));
        r.setAltitudeMinM(f.integer("altitudeMinM", "Độ cao thấp nhất", false, 0, 9000));
        r.setAltitudeMaxM(f.integer("altitudeMaxM", "Độ cao cao nhất", false, 0, 9000));
        r.setSoil(f.optionalText("soil", "Thổ nhưỡng", 0));
        r.setClimate(f.optionalText("climate", "Khí hậu", 0));
        r.setDescription(f.optionalText("description", "Mô tả", 0));
        // Ràng buộc CHECK trong schema: altitude_min_m <= altitude_max_m
        if (r.getAltitudeMinM() != null && r.getAltitudeMaxM() != null
                && r.getAltitudeMinM() > r.getAltitudeMaxM()) {
            f.error("altitudeMaxM", "Độ cao cao nhất phải lớn hơn hoặc bằng độ cao thấp nhất");
        }
        return r;
    }

    @Override protected void insert(GrowingRegion r) throws SQLException { dao.insert(r); }
    @Override protected boolean update(GrowingRegion r) throws SQLException { return dao.update(r); }
    @Override protected boolean delete(int id) throws SQLException { return dao.delete(id); }

    @Override
    protected String usageMessage(int id) throws SQLException {
        int n = dao.countUsage(id);
        return n > 0 ? "đang được dùng bởi " + n + " biến thể sản phẩm" : null;
    }
}
