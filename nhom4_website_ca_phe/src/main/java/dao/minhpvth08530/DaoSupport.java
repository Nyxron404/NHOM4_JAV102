package dao.minhpvth08530;

import uri.DBConnect;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Types;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;

/** Hàm dùng chung cho các DAO: chuẩn hóa dữ liệu theo domain của schema và giao dịch. */
final class DaoSupport {

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9_.-]*$");

    private DaoSupport() {}

    /**
     * Mở kết nối bằng lớp dùng chung của dự án ({@link DBConnect}).
     * <p>DBConnect trả về null khi kết nối hỏng, nên đổi thành SQLException để controller
     * báo "Không kết nối được cơ sở dữ liệu" thay vì lỗi null khó hiểu.</p>
     */
    static Connection openConnection() throws SQLException {
        Connection connection = DBConnect.getConnection();
        if (connection == null) {
            throw new SQLException(
                    "DBConnect.getConnection() trả về null - xem stack trace trong log server",
                    "08006");
        }
        return connection;
    }

    /** Văn bản tiếng Việt bắt buộc: chuẩn Unicode NFC, bỏ khoảng trắng 2 đầu (domain d_name). */
    static String requiredText(String value, String label, int maxLength) {
        String text = optionalText(value, label, maxLength);
        if (text == null) {
            throw new IllegalArgumentException(label + " không được để trống");
        }
        return text;
    }

    /** Văn bản không bắt buộc: rỗng thì lưu NULL (domain d_text/d_note cấm chuỗi rỗng). */
    static String optionalText(String value, String label, int maxLength) {
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

    /** Mã nội bộ (domain d_code): IN HOA không dấu, số, _ - . */
    static String code(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " không được để trống");
        }
        String text = value.strip().toUpperCase().replace(' ', '-');
        if (text.length() > 40 || !CODE.matcher(text).matches()) {
            throw new IllegalArgumentException(
                    label + " chỉ gồm chữ IN HOA không dấu, số và các ký tự _ - . (tối đa 40 ký tự)");
        }
        return text;
    }

    static void setNullableInt(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    static Integer getNullableInt(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    static OffsetDateTime getTime(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class);
    }

    static BigDecimal money(BigDecimal value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " không được để trống");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(label + " không được âm");
        }
        if (value.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(label + " phải là số nguyên (đồng)");
        }
        if (value.precision() - value.scale() > 14) {
            throw new IllegalArgumentException(label + " quá lớn");
        }
        return value.setScale(0);
    }

    /** Công việc chạy trong một giao dịch. */
    interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    /**
     * Chạy nhiều câu lệnh trong cùng một giao dịch: lỗi ở bất kỳ câu nào thì rollback toàn bộ.
     * Nếu bên ngoài đã mở giao dịch thì dùng savepoint để không phá giao dịch đó.
     */
    static <T> T inTransaction(Connection connection, SqlWork<T> work) throws SQLException {
        boolean ownsTransaction = connection.getAutoCommit();
        Savepoint savepoint = null;
        if (ownsTransaction) {
            connection.setAutoCommit(false);
        } else {
            savepoint = connection.setSavepoint();
        }
        try {
            T result = work.run(connection);
            if (ownsTransaction) {
                connection.commit();
            } else {
                connection.releaseSavepoint(savepoint);
            }
            return result;
        } catch (SQLException | RuntimeException exception) {
            if (ownsTransaction) {
                connection.rollback();
            } else if (savepoint != null) {
                connection.rollback(savepoint);
            }
            throw exception;
        } finally {
            if (ownsTransaction) {
                connection.setAutoCommit(true);
            }
        }
    }

    static int update(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            return statement.executeUpdate();
        }
    }

    static boolean exists(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }
}
