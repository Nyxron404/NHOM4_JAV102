package models.quangndth08631;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Doanh thu đã thanh toán (lọc theo ngày thanh toán) theo vùng trồng. */
public class RevenueByGrowingRegionReport {

    private static final String SQL = """
            WITH paid AS (
                SELECT p.sales_invoice_id, MAX(p.paid_at) AS paid_at
                FROM payments p
                WHERE p.status = 'SUCCESS'
                GROUP BY p.sales_invoice_id
            )
            SELECT gr.region_id, gr.region_name,
                   SUM(sil.quantity * sil.unit_price) AS revenue,
                   SUM(sil.quantity) AS quantity_sold
            FROM sales_invoice_lines sil
            JOIN sales_invoices si ON si.sales_invoice_id = sil.sales_invoice_id
            JOIN paid ON paid.sales_invoice_id = si.sales_invoice_id
            JOIN packages pa ON pa.package_id = sil.package_id
            JOIN product_variants pv ON pv.variant_id = pa.variant_id
            JOIN growing_regions gr ON gr.region_id = pv.region_id
            WHERE si.status = 'PAID'
              AND (paid.paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date BETWEEN ? AND ?
            GROUP BY gr.region_id, gr.region_name
            ORDER BY revenue DESC
            """;

    public List<Map<String, Object>> get(Connection connection, LocalDate from, LocalDate to)
            throws SQLException {
        return JdbcReportQuery.query(connection, SQL, from, to);
    }
}
