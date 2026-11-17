package dao.hieulnmth08470;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import models.enums.ActiveStatus;
import models.hieulnmth08470.Supplier;

/** Nhà cung cấp (bảng suppliers) - UC 47-50. */
public class SupplierDAO extends BaseDAO {

    private static final String COLUMNS =
            "supplier_id, supplier_code, supplier_name, tax_code, address, phone, email, status";

    // 1. Xem danh sách: tìm theo tên (không dấu), mã, MST, SĐT; lọc theo trạng thái
    public List<Supplier> search(String keyword, ActiveStatus status) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM suppliers WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (keyword != null) {
            String like = escapeLike(keyword);
            sql.append(" AND (").append(searchCondition("supplier_name"))
               .append(" OR supplier_code ILIKE '%' || ? || '%'")
               .append(" OR tax_code LIKE '%' || ? || '%'")
               .append(" OR phone LIKE '%' || ? || '%')");
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.name());
        }
        sql.append(" ORDER BY status, supplier_name");   // ACTIVE đứng trước INACTIVE

        List<Supplier> list = new ArrayList<>();
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    // 2. Tìm theo ID
    public Supplier getById(int id) throws SQLException {
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT " + COLUMNS + " FROM suppliers WHERE supplier_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // 3. Thêm mới
    public int insert(Supplier s) throws SQLException {
        String sql = "INSERT INTO suppliers (supplier_code, supplier_name, tax_code, address, phone, email, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING supplier_id";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            setValues(ps, s);
            return returningId(ps);
        }
    }

    // 4. Cập nhật
    public boolean update(Supplier s) throws SQLException {
        String sql = "UPDATE suppliers SET supplier_code = ?, supplier_name = ?, tax_code = ?, address = ?, "
                + "phone = ?, email = ?, status = ? WHERE supplier_id = ?";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            setValues(ps, s);
            ps.setInt(8, s.getSupplierId());
            return ps.executeUpdate() > 0;
        }
    }

    /** Ngừng hợp tác / hợp tác lại (dùng khi NCC đã có hóa đơn nên không xóa được). */
    public boolean updateStatus(int id, ActiveStatus status) throws SQLException {
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement("UPDATE suppliers SET status = ? WHERE supplier_id = ?")) {
            ps.setString(1, status.name());
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    // 5. Xóa (chỉ khi chưa có hóa đơn nhập / ánh xạ mã hàng)
    public boolean delete(int id) throws SQLException {
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement("DELETE FROM suppliers WHERE supplier_id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** Số hóa đơn nhập + ánh xạ mã hàng đang tham chiếu NCC. */
    public int countUsage(int id) throws SQLException {
        return count("SELECT (SELECT count(*) FROM purchase_invoices WHERE supplier_id = ?)"
                + " + (SELECT count(*) FROM supplier_item_mappings WHERE supplier_id = ?)", id, id);
    }

    /** supplier_id -> số hóa đơn nhập. */
    public Map<Integer, Integer> invoiceCountMap() throws SQLException {
        return countMap("SELECT supplier_id, count(*) FROM purchase_invoices GROUP BY supplier_id");
    }

    private int count(String sql, int id1, int id2) throws SQLException {
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id1);
            ps.setInt(2, id2);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private void setValues(PreparedStatement ps, Supplier s) throws SQLException {
        ps.setString(1, s.getSupplierCode());
        ps.setString(2, s.getSupplierName());
        setString(ps, 3, s.getTaxCode());
        setString(ps, 4, s.getAddress());
        setString(ps, 5, s.getPhone());
        setString(ps, 6, s.getEmail());
        ps.setString(7, (s.getStatus() == null ? ActiveStatus.ACTIVE : s.getStatus()).name());
    }

    private Supplier mapRow(ResultSet rs) throws SQLException {
        Supplier s = new Supplier();
        s.setSupplierId(rs.getInt("supplier_id"));
        s.setSupplierCode(rs.getString("supplier_code"));
        s.setSupplierName(rs.getString("supplier_name"));
        s.setTaxCode(rs.getString("tax_code"));
        s.setAddress(rs.getString("address"));
        s.setPhone(rs.getString("phone"));
        s.setEmail(rs.getString("email"));
        s.setStatus(ActiveStatus.valueOf(rs.getString("status")));
        return s;
    }
}
