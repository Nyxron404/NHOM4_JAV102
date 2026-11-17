package dao.hieulnmth08470;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uri.DBConnect;

/**
 * Hàm dùng chung cho các DAO phần Danh mục phân loại &amp; Nhà cung cấp (Lê Nguyễn Minh Hiếu).
 * <p>Khác DAO mẫu: các hàm ném SQLException ra ngoài để Servlet báo lỗi rõ ràng
 * (trùng tên, đang được sử dụng...) thay vì chỉ trả về false.</p>
 */
abstract class BaseDAO {

    /** Mở kết nối; DBConnect trả null khi lỗi nên đổi thành SQLException cho dễ xử lý. */
    protected Connection open() throws SQLException {
        Connection con = DBConnect.getConnection();
        if (con == null) {
            throw new SQLException("Không kết nối được cơ sở dữ liệu", "08001");
        }
        return con;
    }

    /** Gán tham số theo thứ tự; null được gán đúng kiểu để PostgreSQL không báo lỗi. */
    protected static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            if (value == null) {
                ps.setNull(i + 1, Types.VARCHAR);
            } else {
                ps.setObject(i + 1, value);
            }
        }
    }

    protected static void setInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) ps.setNull(index, Types.INTEGER); else ps.setInt(index, value);
    }

    protected static void setString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) ps.setNull(index, Types.VARCHAR); else ps.setString(index, value);
    }

    /** rs.getInt trả 0 khi cột NULL, hàm này trả đúng null. */
    protected static Integer getInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * Điều kiện tìm kiếm tiếng Việt không dấu, dùng hàm f_search_key trong schema
     * ("cau dat" tìm ra "Cầu Đất"). Ký tự % và _ người dùng gõ được coi là chữ thường.
     */
    protected static String searchCondition(String column) {
        return "f_search_key(" + column + ") LIKE '%' || f_search_key(?) || '%'";
    }

    protected static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Đếm số dòng đang tham chiếu tới một khóa (dùng trước khi xóa). */
    protected int count(Connection con, String sql, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    protected int count(String sql, int id) throws SQLException {
        try (Connection con = open()) {
            return count(con, sql, id);
        }
    }

    /** Chạy câu SELECT khóa, số_lượng ... GROUP BY khóa và trả về Map (để hiện số lượng trên bảng). */
    protected Map<Integer, Integer> countMap(String sql) throws SQLException {
        Map<Integer, Integer> map = new HashMap<>();
        try (Connection con = open();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getInt(1), rs.getInt(2));
            }
        }
        return map;
    }

    /** Dùng cho INSERT ... RETURNING id. */
    protected static int returningId(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("CSDL không trả về khóa vừa tạo");
            }
            return rs.getInt(1);
        }
    }

    /** Chạy nhiều câu lệnh trong 1 giao dịch: lỗi ở đâu thì hoàn tác toàn bộ. */
    protected interface TxWork<R> {
        R run(Connection con) throws SQLException;
    }

    protected <R> R inTransaction(TxWork<R> work) throws SQLException {
        try (Connection con = open()) {
            con.setAutoCommit(false);
            try {
                R result = work.run(con);
                con.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }
}
