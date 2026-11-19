package dao.hieuttth08297;

import uri.DBConnect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class KhachBaoLoiDAO {

    public void insert(String token, String note, String userAgent) throws SQLException {
        try (Connection con = DBConnect.getConnection()) {
            if (con == null) {
                throw new SQLException("Không kết nối được cơ sở dữ liệu", "08001");
            }
            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO qr_invalid_reports (scanned_token, note, user_agent)
                    VALUES (?, ?, ?)""")) {
                ps.setString(1, token);
                ps.setString(2, note);
                ps.setString(3, userAgent);
                ps.executeUpdate();
            }
        }
    }
}