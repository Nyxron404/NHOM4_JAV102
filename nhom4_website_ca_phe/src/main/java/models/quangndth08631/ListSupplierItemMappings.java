package models.quangndth08631;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Danh sách ánh xạ mã hàng của một nhà cung cấp. */
public class ListSupplierItemMappings {

    private static final String SQL = """
            SELECT m.mapping_id, m.supplier_id, s.supplier_name,
                   m.supplier_item_code, m.package_id, p.sku, p.status AS package_status
            FROM supplier_item_mappings m
            JOIN suppliers s ON s.supplier_id = m.supplier_id
            JOIN packages p ON p.package_id = m.package_id
            WHERE m.supplier_id = ?
            ORDER BY m.supplier_item_code
            """;

    public List<Map<String, Object>> get(Connection connection, int supplierId)
            throws SQLException {
        return JdbcReportQuery.query(connection, SQL, supplierId);
    }
}
