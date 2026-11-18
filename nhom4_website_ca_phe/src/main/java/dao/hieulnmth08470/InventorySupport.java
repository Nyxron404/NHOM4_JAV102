package dao.hieulnmth08470;

import uri.DBConnect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Types;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hàm dùng chung cho phần Tồn kho, kiểm kê và thông báo (UC105–UC129, Lê Nguyễn Minh Hiếu).
 * <p>Cùng cách làm với dao.minhpvth08530.DaoSupport / AccountDAO: mở kết nối bằng DBConnect,
 * chạy nhiều câu trong một giao dịch, trả kết quả dạng Map khóa camelCase để controller
 * chuyển thẳng sang JSON.</p>
 */
final class InventorySupport {

    private InventorySupport() {}

    /** Mở kết nối; DBConnect trả null khi lỗi nên đổi thành SQLException để báo "Không kết nối được". */
    static Connection open() throws SQLException {
        Connection connection = DBConnect.getConnection();
        if (connection == null) {
            throw new SQLException("DBConnect.getConnection() trả về null", "08006");
        }
        return connection;
    }

    /** Công việc chạy trong một giao dịch. */
    interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    /** Lỗi ở bất kỳ câu nào thì rollback toàn bộ (giống DaoSupport.inTransaction). */
    static <T> T inTransaction(Connection connection, SqlWork<T> work) throws SQLException {
        boolean owns = connection.getAutoCommit();
        Savepoint savepoint = null;
        if (owns) {
            connection.setAutoCommit(false);
        } else {
            savepoint = connection.setSavepoint();
        }
        try {
            T result = work.run(connection);
            if (owns) {
                connection.commit();
            } else {
                connection.releaseSavepoint(savepoint);
            }
            return result;
        } catch (SQLException | RuntimeException exception) {
            if (owns) {
                connection.rollback();
            } else if (savepoint != null) {
                connection.rollback(savepoint);
            }
            RuntimeException friendly = stockError(exception);
            if (friendly != null) {
                throw friendly;
            }
            throw exception;
        } finally {
            if (owns) {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * Trigger trg_no_negative_stock báo "Lô x không đủ tồn" (SQLState P0001): đổi thành lỗi nghiệp vụ
     * để trang web hiện đúng câu tiếng Việt thay vì "Lỗi cơ sở dữ liệu".
     */
    static RuntimeException stockError(Exception exception) {
        if (exception instanceof SQLException sql && "P0001".equals(sql.getSQLState())
                && String.valueOf(sql.getMessage()).contains("không đủ tồn")) {
            return new IllegalStateException(
                    "Tồn hiện tại của lô đã thay đổi (vừa bán hoặc xuất hủy) nên không đủ để trừ. "
                            + "Hãy từ chối phiếu này và tạo phiếu mới.");
        }
        return null;
    }

    // ------------------------------------------------------------------ chạy câu lệnh

    static List<Map<String, Object>> list(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                ResultSetMetaData meta = resultSet.getMetaData();
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int column = 1; column <= meta.getColumnCount(); column++) {
                        row.put(camel(meta.getColumnLabel(column)), value(resultSet, meta, column));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    static Map<String, Object> one(Connection connection, String sql, Object... parameters)
            throws SQLException {
        List<Map<String, Object>> rows = list(connection, sql, parameters);
        return rows.isEmpty() ? null : rows.get(0);
    }

    static int exec(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            return statement.executeUpdate();
        }
    }

    /** Giá trị null phải có kiểu để PostgreSQL hiểu được CAST(? AS INT) IS NULL... */
    static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            Object value = parameters[index];
            if (value == null) {
                statement.setNull(index + 1, Types.VARCHAR);
            } else if (value instanceof LocalDate date) {
                statement.setObject(index + 1, date, Types.DATE);
            } else {
                statement.setObject(index + 1, value);
            }
        }
    }

    private static Object value(ResultSet resultSet, ResultSetMetaData meta, int column) throws SQLException {
        String type = meta.getColumnTypeName(column);
        if ("timestamptz".equals(type) || meta.getColumnType(column) == Types.TIMESTAMP_WITH_TIMEZONE) {
            return resultSet.getObject(column, OffsetDateTime.class);
        }
        if (meta.getColumnType(column) == Types.DATE) {
            return resultSet.getObject(column, LocalDate.class);   // java.sql.Date -> "2026-11-19" trong JSON
        }
        if ("jsonb".equals(type) || "json".equals(type)) {
            return resultSet.getString(column);
        }
        return resultSet.getObject(column);
    }

    private static String camel(String column) {
        StringBuilder builder = new StringBuilder();
        boolean upper = false;
        for (char ch : column.toCharArray()) {
            if (ch == '_') {
                upper = true;
            } else {
                builder.append(upper ? Character.toUpperCase(ch) : ch);
                upper = false;
            }
        }
        return builder.toString();
    }

    // ------------------------------------------------------------------ chuẩn hóa dữ liệu

    /** Ghi chú không bắt buộc (domain d_note cấm chuỗi rỗng): rỗng -> NULL. */
    static String note(String value, String label, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = Normalizer.normalize(value, Normalizer.Form.NFC).strip();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > maxLength) {
            throw new IllegalArgumentException(label + " tối đa " + maxLength + " ký tự");
        }
        return text;
    }

    static String requiredNote(String value, String label, int maxLength) {
        String text = note(value, label, maxLength);
        if (text == null) {
            throw new IllegalArgumentException("Nhập " + label.toLowerCase());
        }
        return text;
    }

    static int nonNegative(Integer value, String label) {
        if (value == null) {
            throw new IllegalArgumentException("Nhập " + label.toLowerCase());
        }
        if (value < 0) {
            throw new IllegalArgumentException(label + " không được âm");
        }
        return value;
    }

    /** Điều chỉnh độ dài trang (UC127): 1..100, mặc định 20. */
    static int pageSize(Integer size) {
        return size == null ? 20 : Math.max(1, Math.min(100, size));
    }

    static int page(Integer page) {
        return page == null ? 1 : Math.max(1, page);
    }
}
