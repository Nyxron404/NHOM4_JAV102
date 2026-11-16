package models.quangndth08631;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Sản phẩm bán chạy, xếp theo số lượng bán trong khoảng ngày. */
public class BestSellingProductsReport {

    private static final String SQL = """
            SELECT pr.product_id, pr.product_code, pr.product_name,
                   SUM(sil.quantity) AS quantity_sold,
                   SUM(sil.quantity * sil.unit_price) AS sales_amount
            FROM sales_invoice_lines sil
            JOIN sales_invoices si ON si.sales_invoice_id = sil.sales_invoice_id
            JOIN packages pa ON pa.package_id = sil.package_id
            JOIN product_variants pv ON pv.variant_id = pa.variant_id
            JOIN products pr ON pr.product_id = pv.product_id
            WHERE si.status = 'PAID'
              AND (si.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date BETWEEN ? AND ?
            GROUP BY pr.product_id, pr.product_code, pr.product_name
            ORDER BY quantity_sold DESC, sales_amount DESC
            """;

    public List<Map<String, Object>> get(Connection connection, LocalDate from, LocalDate to)
            throws SQLException {
        return JdbcReportQuery.query(connection, SQL, from, to);
    }
}
