package dao.hieulnmth08470;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import models.hieulnmth08470.catalog.Province;

/** Tỉnh/thành: chỉ đọc, dùng làm danh sách chọn khi thêm/sửa vùng trồng. */
public class ProvinceDAO extends BaseDAO {

    public List<Province> getAll() throws SQLException {
        List<Province> list = new ArrayList<>();
        String sql = "SELECT province_id, province_name FROM provinces ORDER BY province_name";
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Province p = new Province();
                p.setProvinceId(rs.getInt("province_id"));
                p.setProvinceName(rs.getString("province_name"));
                list.add(p);
            }
        }
        return list;
    }
}
