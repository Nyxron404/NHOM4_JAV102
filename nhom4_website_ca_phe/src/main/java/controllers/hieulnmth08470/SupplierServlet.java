package controllers.hieulnmth08470;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

import dao.hieulnmth08470.SupplierDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import models.enums.ActiveStatus;
import models.hieulnmth08470.Supplier;

/**
 * UC 47-50: Xem / Thêm / Sửa / Xóa nhà cung cấp.
 * NCC đã có hóa đơn nhập thì không xóa được (giữ lịch sử) -> dùng "Ngừng hợp tác" (status = INACTIVE).
 * POST action=status&amp;id=..&amp;status=ACTIVE|INACTIVE -> đổi trạng thái hợp tác.
 */
@WebServlet("/api/danh-muc/nha-cung-cap")
public class SupplierServlet extends CrudServlet<Supplier> {

    private final SupplierDAO dao = new SupplierDAO();

    @Override protected String label() { return "nhà cung cấp"; }

    @Override
    protected void loadList(HttpServletRequest req, Map<String, Object> data) throws SQLException {
        ActiveStatus status = parseStatus(req.getParameter("status"));
        data.put("items", dao.search(Form.clean(req.getParameter("q")), status));
        data.put("usage", dao.invoiceCountMap());
        data.put("status", status == null ? "" : status.name());
    }

    @Override protected Supplier newItem() { return new Supplier(); }
    @Override protected Supplier findById(int id) throws SQLException { return dao.getById(id); }

    @Override
    protected Supplier bind(Form f, Integer id) {
        Supplier s = new Supplier();
        s.setSupplierId(id);
        s.setSupplierCode(f.code("supplierCode", "Mã nhà cung cấp"));
        s.setSupplierName(f.name("supplierName", "Tên nhà cung cấp"));
        s.setTaxCode(f.taxCode("taxCode", "Mã số thuế"));
        s.setAddress(f.optionalText("address", "Địa chỉ", 255));
        s.setPhone(f.phone("phone", "Số điện thoại"));
        s.setEmail(f.email("email", "Email"));
        ActiveStatus status = parseStatus(f.raw("status"));
        s.setStatus(status == null ? ActiveStatus.ACTIVE : status);
        return s;
    }

    @Override protected void insert(Supplier s) throws SQLException { dao.insert(s); }
    @Override protected boolean update(Supplier s) throws SQLException { return dao.update(s); }
    @Override protected boolean delete(int id) throws SQLException { return dao.delete(id); }

    @Override
    protected String usageMessage(int id) throws SQLException {
        int n = dao.countUsage(id);
        return n > 0
                ? "đã có " + n + " hóa đơn nhập / mã hàng liên kết. Hãy dùng \"Ngừng hợp tác\" thay vì xóa"
                : null;
    }

    /** POST action=status&amp;id=..&amp;status=ACTIVE|INACTIVE: ngừng / tiếp tục hợp tác. */
    @Override
    protected boolean handleExtraPost(String action, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, SQLException {
        if (!"status".equals(action)) return false;
        Integer id = Form.parseId(req.getParameter("id"));
        ActiveStatus status = parseStatus(req.getParameter("status"));
        if (id == null || status == null) {
            fail(resp, 400, "Thao tác không hợp lệ");
        } else if (dao.updateStatus(id, status)) {
            ok(resp, status == ActiveStatus.ACTIVE
                    ? "Đã chuyển nhà cung cấp về trạng thái đang hợp tác"
                    : "Đã ngừng hợp tác với nhà cung cấp");
        } else {
            fail(resp, 404, "Không tìm thấy nhà cung cấp");
        }
        return true;
    }

    private static ActiveStatus parseStatus(String raw) {
        if (raw == null) return null;
        try {
            return ActiveStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
