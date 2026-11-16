package models.quangndth08631;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Danh sách khoản chi phát sinh từ hóa đơn nhập đã xác nhận và lỗ xuất hủy. */
public class ExpenseListReport {

    private static final String SQL = """
            SELECT expense_date, expense_type, reference_id, supplier_id, amount
            FROM (
                SELECT (pit.confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS expense_date,
                       'PURCHASE'::text AS expense_type,
                       pit.purchase_invoice_id AS reference_id,
                       pit.supplier_id,
                       pit.total_amount AS amount
                FROM v_purchase_invoice_totals pit
                WHERE pit.status = 'CONFIRMED'
                UNION ALL
                SELECT (dl.disposed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
                       'DISPOSAL_LOSS'::text,
                       dl.disposal_id,
                       NULL::int,
                       dl.loss_amount
                FROM v_disposal_losses dl
            ) expenses
            WHERE expense_date BETWEEN ? AND ?
            ORDER BY expense_date DESC, expense_type, reference_id
            """;

    public List<Map<String, Object>> get(Connection connection, LocalDate from, LocalDate to)
            throws SQLException {
        return JdbcReportQuery.query(connection, SQL, from, to);
    }
}
