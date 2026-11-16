package models.quangndth08631;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

/** Tạo phiếu nhập nháp từ thông tin nhập tay. */
public class CreateManualPurchaseInvoice {

    private static final String SQL = """
            INSERT INTO purchase_invoices
                (supplier_id, invoice_no, invoice_date, input_method, created_by)
            VALUES (?, ?, ?, 'MANUAL', ?)
            """;

    public long create(Connection connection, int supplierId, String invoiceNo,
                       LocalDate invoiceDate, int createdBy) throws SQLException {
        if (invoiceNo == null || invoiceNo.isBlank()) {
            throw new IllegalArgumentException("invoiceNo is required");
        }
        if (invoiceDate == null) {
            throw new IllegalArgumentException("invoiceDate is required");
        }
        try (PreparedStatement statement = connection.prepareStatement(SQL,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, supplierId);
            statement.setString(2, invoiceNo.trim());
            statement.setObject(3, invoiceDate);
            statement.setInt(4, createdBy);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Database did not return the purchase invoice id");
                }
                return keys.getLong(1);
            }
        }
    }
}
