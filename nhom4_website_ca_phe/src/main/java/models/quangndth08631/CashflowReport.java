package models.quangndth08631;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Tổng hợp thu, chi nhập hàng, lỗ hủy và dòng tiền ròng theo ngày. */
public class CashflowReport {

    private static final String SQL = """
            WITH entries AS (
                SELECT (p.paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS entry_date,
                       p.amount AS income, 0::numeric AS purchase_expense,
                       0::numeric AS disposal_loss
                FROM payments p
                WHERE p.status = 'SUCCESS'
                UNION ALL
                SELECT (pit.confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
                       0::numeric, pit.total_amount, 0::numeric
                FROM v_purchase_invoice_totals pit
                WHERE pit.status = 'CONFIRMED'
                UNION ALL
                SELECT (dl.disposed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
                       0::numeric, 0::numeric, dl.loss_amount
                FROM v_disposal_losses dl
            )
            SELECT entry_date,
                   SUM(income) AS income,
                   SUM(purchase_expense) AS purchase_expense,
                   SUM(disposal_loss) AS disposal_loss,
                   SUM(income - purchase_expense - disposal_loss) AS net_cashflow
            FROM entries
            WHERE entry_date BETWEEN ? AND ?
            GROUP BY entry_date
            ORDER BY entry_date
            """;

    public List<Map<String, Object>> get(Connection connection, LocalDate from, LocalDate to)
            throws SQLException {
        return JdbcReportQuery.query(connection, SQL, from, to);
    }
}
