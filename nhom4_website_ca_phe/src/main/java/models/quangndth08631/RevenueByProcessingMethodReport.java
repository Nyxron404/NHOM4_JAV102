package models.quangndth08631;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Doanh thu đã thanh toán theo phương pháp sơ chế. */
public class RevenueByProcessingMethodReport {

    private static final String SQL = """
            SELECT pm.process_id, pm.process_name,
                   SUM(sil.quantity * sil.unit_price) AS revenue,
                   SUM(sil.quantity) AS quantity_sold
            FROM sales_invoice_lines sil
            JOIN sales_invoices si ON si.sales_invoice_id = sil.sales_invoice_id
            JOIN packages pa ON pa.package_id = sil.package_id
            JOIN product_variants pv ON pv.variant_id = pa.variant_id
            JOIN processing_methods pm ON pm.process_id = pv.process_id
            WHERE si.status = 'PAID'
              AND (si.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date BETWEEN ? AND ?
            GROUP BY pm.process_id, pm.process_name
            ORDER BY revenue DESC
            """;

    public List<Map<String, Object>> get(Connection connection, LocalDate from, LocalDate to)
            throws SQLException {
        return JdbcReportQuery.query(connection, SQL, from, to);
    }
}
