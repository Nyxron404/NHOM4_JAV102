package models.quangndth08631;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Tổng doanh thu đã thanh toán trong khoảng ngày, tính cả hai ngày biên. */
public class RevenueByPeriodReport {

    private static final String SQL = """
            SELECT COALESCE(SUM(p.amount), 0) AS revenue,
                   COUNT(DISTINCT p.sales_invoice_id) AS invoice_count
            FROM payments p
            WHERE p.status = 'SUCCESS'
              AND (p.paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date BETWEEN ? AND ?
            """;

    public List<Map<String, Object>> get(Connection connection, LocalDate from, LocalDate to)
            throws SQLException {
        return JdbcReportQuery.query(connection, SQL, from, to);
    }
}
