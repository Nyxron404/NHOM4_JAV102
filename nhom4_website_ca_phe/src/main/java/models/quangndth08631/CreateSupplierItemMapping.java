package models.quangndth08631;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Tạo ánh xạ mã hàng nhà cung cấp sang gói bán. */
public class CreateSupplierItemMapping {

    private static final String SQL = """
            INSERT INTO supplier_item_mappings
                (supplier_id, supplier_item_code, package_id)
            VALUES (?, ?, ?)
            """;

    public int create(Connection connection, int supplierId, String supplierItemCode,
                      int packageId) throws SQLException {
        if (supplierItemCode == null || supplierItemCode.isBlank()) {
            throw new IllegalArgumentException("supplierItemCode is required");
        }
        try (PreparedStatement statement = connection.prepareStatement(SQL,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, supplierId);
            statement.setString(2, supplierItemCode.trim());
            statement.setInt(3, packageId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Database did not return the mapping id");
                }
                return keys.getInt(1);
            }
        }
    }
}
