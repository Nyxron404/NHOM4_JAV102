package models.quangndth08631;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Doanh thu đã thanh toán theo mức rang. */
public class RevenueByRoastLevelReport {

    private static final String SQL = """
            SELECT rl.roast_level_id, rl.roast_name, rl.sort_order,
                   SUM(sil.quantity * sil.unit_price) AS revenue,
                   SUM(sil.quantity) AS quantity_sold
            FROM sales_invoice_lines sil
            JOIN sales_invoices si ON si.sales_invoice_id = sil.sales_invoice_id
            JOIN packages pa ON pa.package_id = sil.package_id
            JOIN product_variants pv ON pv.variant_id = pa.variant_id
            JOIN roast_levels rl ON rl.roast_level_id = pv.roast_level_id
            WHERE si.status = 'PAID'
              AND (si.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date BETWEEN ? AND ?
            GROUP BY rl.roast_level_id, rl.roast_name, rl.sort_order
            ORDER BY rl.sort_order
            """;

    public List<Map<String, Object>> get(Connection connection, LocalDate from, LocalDate to)
            throws SQLException {
        return JdbcReportQuery.query(connection, SQL, from, to);
    }
}
