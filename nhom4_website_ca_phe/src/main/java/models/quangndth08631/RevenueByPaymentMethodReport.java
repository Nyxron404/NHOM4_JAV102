package models.quangndth08631;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Doanh thu và số giao dịch theo phương thức thanh toán thành công. */
public class RevenueByPaymentMethodReport {

    private static final String SQL = """
            SELECT pm.payment_method_id, pm.method_code, pm.method_name,
                   SUM(p.amount) AS revenue,
                   COUNT(*) AS payment_count,
                   COUNT(DISTINCT p.sales_invoice_id) AS invoice_count
            FROM payments p
            JOIN payment_methods pm ON pm.payment_method_id = p.payment_method_id
            WHERE p.status = 'SUCCESS'
            GROUP BY pm.payment_method_id, pm.method_code, pm.method_name
            ORDER BY revenue DESC, pm.method_name
            """;

    public List<Map<String, Object>> get(Connection connection) throws SQLException {
        return JdbcReportQuery.query(connection, SQL);
    }
}
