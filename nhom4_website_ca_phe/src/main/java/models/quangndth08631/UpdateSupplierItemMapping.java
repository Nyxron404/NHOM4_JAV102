package models.quangndth08631;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Sửa gói bán được ánh xạ cho một mã hàng nhà cung cấp. */
public class UpdateSupplierItemMapping {

    private static final String SQL = """
            UPDATE supplier_item_mappings
            SET supplier_item_code = ?, package_id = ?
            WHERE mapping_id = ? AND supplier_id = ?
            """;

    public boolean update(Connection connection, int mappingId, int supplierId,
                          String supplierItemCode, int packageId) throws SQLException {
        if (supplierItemCode == null || supplierItemCode.isBlank()) {
            throw new IllegalArgumentException("supplierItemCode is required");
        }
        try (PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setString(1, supplierItemCode.trim());
            statement.setInt(2, packageId);
            statement.setInt(3, mappingId);
            statement.setInt(4, supplierId);
            return statement.executeUpdate() == 1;
        }
    }
}
