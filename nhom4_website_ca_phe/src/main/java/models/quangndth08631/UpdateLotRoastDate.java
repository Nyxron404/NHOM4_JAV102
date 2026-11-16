package models.quangndth08631;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;

/** Cập nhật ngày rang của một lô. */
public class UpdateLotRoastDate {

    private static final String SQL = """
            UPDATE lots
            SET roast_date = ?
            WHERE lot_id = ?
                            AND (CAST(? AS date) IS NULL OR ? <= expiry_date)
            """;

    public boolean update(Connection connection, long lotId, LocalDate roastDate)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setObject(1, roastDate);
            statement.setLong(2, lotId);
            statement.setObject(3, roastDate);
            statement.setObject(4, roastDate);
            return statement.executeUpdate() == 1;
        }
    }
}
