package models.quangndth08631;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Danh sách lô còn tồn có hạn dùng trong số ngày tới do người gọi chọn. */
public class NearExpiryInventoryReport {

    private static final String SQL = """
            SELECT ls.lot_id, ls.lot_code, ls.package_id, p.sku,
                   ls.roast_date, ls.expiry_date, ls.quantity_on_hand,
                   ls.unit_cost,
                   (ls.expiry_date - CURRENT_DATE) AS days_until_expiry
            FROM v_lot_stock ls
            JOIN packages p ON p.package_id = ls.package_id
            WHERE ls.quantity_on_hand > 0
              AND ls.expiry_date >= CURRENT_DATE
              AND ls.expiry_date <= CURRENT_DATE + ?
            ORDER BY ls.expiry_date, p.sku
            """;

    public List<Map<String, Object>> get(Connection connection, int withinDays) throws SQLException {
        if (withinDays < 0) {
            throw new IllegalArgumentException("withinDays must not be negative");
        }
        return JdbcReportQuery.query(connection, SQL, withinDays);
    }
}
