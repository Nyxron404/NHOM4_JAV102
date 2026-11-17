package dao.minhpvth08530;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Mở kết nối PostgreSQL cho phần quản lý sản phẩm.
 * <p>Thứ tự ưu tiên:</p>
 * <ol>
 *   <li>Cấu hình DB_URL / DB_USER / DB_PASSWORD (system property hoặc biến môi trường),
 *       dùng khi muốn trỏ sang database khác mà không sửa code.</li>
 *   <li>Lớp kết nối chung của nhóm {@code uri.DBConnect#getConnection()} nếu có trong project
 *       (gọi bằng reflection để phần này vẫn biên dịch được khi thiếu lớp đó).</li>
 *   <li>Mặc định: PostgreSQL ở localhost.</li>
 * </ol>
 */
public final class DBContext {

    private static final String SHARED_CLASS = "uri.DBConnect";
    private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/coffee_shop";

    /** Lấy sẵn một lần: null nghĩa là project không có lớp kết nối chung. */
    private static final Method SHARED_METHOD = findSharedMethod();

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException exception) {
            throw new ExceptionInInitializerError("Thiếu thư viện postgresql trong WEB-INF/lib");
        }
    }

    private DBContext() {}

    public static Connection getConnection() throws SQLException {
        String url = setting("DB_URL", null);
        if (url == null && SHARED_METHOD != null) {
            return shared();
        }
        Connection connection = DriverManager.getConnection(
                url == null ? DEFAULT_URL : url,
                setting("DB_USER", "postgres"),
                setting("DB_PASSWORD", ""));
        connection.setAutoCommit(true);
        return connection;
    }

    /** Gọi uri.DBConnect.getConnection(); lớp đó trả null khi lỗi nên phải kiểm tra lại. */
    private static Connection shared() throws SQLException {
        Object result;
        try {
            result = SHARED_METHOD.invoke(null);
        } catch (IllegalAccessException exception) {
            throw new SQLException("Không gọi được " + SHARED_CLASS + ".getConnection()", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof SQLException sqlException) {
                throw sqlException;
            }
            throw new SQLException("Lỗi trong " + SHARED_CLASS + ".getConnection()", "08006", cause);
        }
        if (result == null) {
            // DBConnect nuốt lỗi và trả null -> xem stack trace trong log của Tomcat
            throw new SQLException(SHARED_CLASS + ".getConnection() trả về null"
                    + " (sai thông tin kết nối hoặc không tới được database)", "08006");
        }
        Connection connection = (Connection) result;
        connection.setAutoCommit(true);
        return connection;
    }

    private static Method findSharedMethod() {
        try {
            Method method = Class.forName(SHARED_CLASS).getMethod("getConnection");
            return Connection.class.isAssignableFrom(method.getReturnType()) ? method : null;
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            return null;
        }
    }

    private static String setting(String key, String fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key);
        }
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
