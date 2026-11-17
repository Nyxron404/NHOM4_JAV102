package uri;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnect {
    private static final String URL = "jdbc:postgresql://db.relokaaarryqkpscddig.supabase.co:5432/postgres?sslmode=require";
    private static final String USER = "postgres";
    private static final String PASSWORD = "_6X44qE@*XB5U+8";

    public static Connection getConnection() {
        Connection con = null;
        try {
            Class.forName("org.postgresql.Driver");
            con = DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (ClassNotFoundException e) {
            System.err.println("Không tìm thấy PostgreSQL JDBC Driver!");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Kết nối tới Supabase thất bại!");
            e.printStackTrace();
        }
        return con;
    }

    // Chạy thử để kiểm tra kết nối
    public static void main(String[] args) {
        try (Connection con = DBConnect.getConnection()) {
            if (con != null) {
                System.out.println("Kết nối Supabase thành công!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
