package models.quangndth08631;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Lợi nhuận = doanh thu đã thu - giá vốn hàng bán - giá trị lỗ xuất hủy. */
public class ProfitReport {

    private static final String SQL = """
            WITH revenue AS (
                SELECT COALESCE(SUM(p.amount), 0) AS amount
                FROM payments p
                WHERE p.status = 'SUCCESS'
                  AND (p.paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date BETWEEN ? AND ?
            ), cost_of_sales AS (
                SELECT COALESCE(SUM(a.quantity * pil.unit_cost), 0) AS amount
                FROM sales_line_allocations a
                JOIN sales_invoice_lines sil ON sil.sales_line_id = a.sales_line_id
                JOIN sales_invoices si ON si.sales_invoice_id = sil.sales_invoice_id
                JOIN (
                    SELECT p.sales_invoice_id, MAX(p.paid_at) AS paid_at
                    FROM payments p
                    WHERE p.status = 'SUCCESS'
                    GROUP BY p.sales_invoice_id
                ) paid ON paid.sales_invoice_id = si.sales_invoice_id
                JOIN lots l ON l.lot_id = a.lot_id
                JOIN purchase_invoice_lines pil ON pil.purchase_line_id = l.purchase_line_id
                WHERE si.status = 'PAID'
                  AND (paid.paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date BETWEEN ? AND ?
            ), disposal AS (
                SELECT COALESCE(SUM(dl.loss_amount), 0) AS amount
                FROM v_disposal_losses dl
                WHERE (dl.disposed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date BETWEEN ? AND ?
            )
            SELECT revenue.amount AS revenue,
                   cost_of_sales.amount AS cost_of_sales,
                   disposal.amount AS disposal_loss,
                   revenue.amount - cost_of_sales.amount - disposal.amount AS profit
            FROM revenue CROSS JOIN cost_of_sales CROSS JOIN disposal
            """;

    public List<Map<String, Object>> get(Connection connection, LocalDate from, LocalDate to)
            throws SQLException {
        return JdbcReportQuery.query(connection, SQL, from, to, from, to, from, to);
    }
}
