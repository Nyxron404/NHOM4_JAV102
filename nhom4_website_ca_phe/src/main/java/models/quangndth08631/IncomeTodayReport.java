package models.quangndth08631;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/** Báo cáo khoản thu thành công trong ngày theo giờ Việt Nam. */
public class IncomeTodayReport {

    private static final String SQL = """
            SELECT COALESCE(SUM(p.amount), 0) AS total_income,
                   COUNT(DISTINCT p.sales_invoice_id) AS paid_invoice_count
            FROM payments p
            WHERE p.status = 'SUCCESS'
              AND (p.paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date = ?
            """;

    public Result get(Connection connection, LocalDate day) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setDate(1, Date.valueOf(day));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new Result(BigDecimal.ZERO, 0L);
                }
                return new Result(
                        resultSet.getBigDecimal("total_income"),
                        resultSet.getLong("paid_invoice_count"));
            }
        }
    }

    public record Result(BigDecimal totalIncome, long paidInvoiceCount) {}
}
