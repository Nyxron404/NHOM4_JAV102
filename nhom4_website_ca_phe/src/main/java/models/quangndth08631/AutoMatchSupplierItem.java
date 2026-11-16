package models.quangndth08631;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Tự tìm gói bán đã ánh xạ theo nhà cung cấp và mã hàng. */
public class AutoMatchSupplierItem {

    private static final String SQL = """
            SELECT m.mapping_id, m.package_id, p.sku
            FROM supplier_item_mappings m
            JOIN packages p ON p.package_id = m.package_id
            WHERE m.supplier_id = ? AND m.supplier_item_code = ?
            """;

    public List<Map<String, Object>> find(Connection connection, int supplierId,
                                          String supplierItemCode) throws SQLException {
        return JdbcReportQuery.query(connection, SQL, supplierId, supplierItemCode);
    }
}
