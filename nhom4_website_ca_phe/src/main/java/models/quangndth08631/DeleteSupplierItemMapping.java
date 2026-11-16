package models.quangndth08631;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Xóa ánh xạ mã hàng theo nhà cung cấp. */
public class DeleteSupplierItemMapping {

    private static final String SQL = """
            DELETE FROM supplier_item_mappings
            WHERE mapping_id = ? AND supplier_id = ?
            """;

    public boolean delete(Connection connection, int mappingId, int supplierId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setInt(1, mappingId);
            statement.setInt(2, supplierId);
            return statement.executeUpdate() == 1;
        }
    }
}
