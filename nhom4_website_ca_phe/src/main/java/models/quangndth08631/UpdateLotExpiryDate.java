package models.quangndth08631;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;

/** Cập nhật hạn sử dụng của một lô, không trước ngày rang. */
public class UpdateLotExpiryDate {

    private static final String SQL = """
            UPDATE lots
            SET expiry_date = ?
            WHERE lot_id = ?
              AND (roast_date IS NULL OR roast_date <= ?)
            """;

    public boolean update(Connection connection, long lotId, LocalDate expiryDate)
            throws SQLException {
        if (expiryDate == null) {
            throw new IllegalArgumentException("expiryDate is required");
        }
        try (PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setObject(1, expiryDate);
            statement.setLong(2, lotId);
            statement.setObject(3, expiryDate);
            return statement.executeUpdate() == 1;
        }
    }
}
