package models.quangndth08631;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Doanh thu đã thanh toán, nhóm theo tháng trong một năm. */
public class RevenueByMonthReport {

    private static final String SQL = """
            SELECT EXTRACT(YEAR FROM local_paid_at)::int AS revenue_year,
                   EXTRACT(MONTH FROM local_paid_at)::int AS revenue_month,
                   SUM(amount) AS revenue,
                   COUNT(DISTINCT sales_invoice_id) AS invoice_count
            FROM (
                SELECT p.amount, p.sales_invoice_id,
                       p.paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh' AS local_paid_at
                FROM payments p
                WHERE p.status = 'SUCCESS'
            ) paid
            WHERE EXTRACT(YEAR FROM local_paid_at) = ?
            GROUP BY revenue_year, revenue_month
            ORDER BY revenue_month
            """;

    public List<Map<String, Object>> get(Connection connection, int year) throws SQLException {
        return JdbcReportQuery.query(connection, SQL, year);
    }
}
