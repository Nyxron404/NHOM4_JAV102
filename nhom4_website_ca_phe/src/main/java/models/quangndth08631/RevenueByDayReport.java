package models.quangndth08631;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Doanh thu đã thanh toán, nhóm theo ngày giờ Việt Nam. */
public class RevenueByDayReport {

    private static final String SQL = """
            SELECT (p.paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS revenue_day,
                   SUM(p.amount) AS revenue,
                   COUNT(DISTINCT p.sales_invoice_id) AS invoice_count
            FROM payments p
            WHERE p.status = 'SUCCESS'
              AND (p.paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date BETWEEN ? AND ?
            GROUP BY revenue_day
            ORDER BY revenue_day
            """;

    public List<Map<String, Object>> get(Connection connection, LocalDate from, LocalDate to)
            throws SQLException {
        return JdbcReportQuery.query(connection, SQL, from, to);
    }
}
