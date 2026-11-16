package models.quangndth08631;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Tồn kho hiện tại theo gói và theo lô từ các view tồn dẫn xuất. */
public class InventoryReport {

    private static final String SQL = """
            SELECT p.package_id, p.sku, p.min_stock_level,
                   ps.quantity_on_hand AS package_quantity_on_hand,
                   ps.is_low_stock,
                   ls.lot_id, ls.lot_code, ls.roast_date, ls.expiry_date,
                   ls.unit_cost, ls.quantity_on_hand AS lot_quantity_on_hand
            FROM packages p
            JOIN v_package_stock ps ON ps.package_id = p.package_id
            LEFT JOIN v_lot_stock ls ON ls.package_id = p.package_id
            ORDER BY p.sku, ls.expiry_date, ls.lot_id
            """;

    public List<Map<String, Object>> get(Connection connection) throws SQLException {
        return JdbcReportQuery.query(connection, SQL);
    }
}
