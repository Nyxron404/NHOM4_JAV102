package models.quangndth08631;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

/** Tạo phiếu nhập nháp từ mã tra cứu hóa đơn nhà cung cấp. */
public class CreateLookupPurchaseInvoice {

    private static final String SQL = """
            INSERT INTO purchase_invoices
                (supplier_id, invoice_no, lookup_code, invoice_date, input_method, created_by)
            VALUES (?, ?, ?, ?, 'LOOKUP', ?)
            """;

    public long create(Connection connection, int supplierId, String invoiceNo,
                       String lookupCode, LocalDate invoiceDate, int createdBy)
            throws SQLException {
        if (invoiceNo == null || invoiceNo.isBlank()
                || lookupCode == null || lookupCode.isBlank() || invoiceDate == null) {
            throw new IllegalArgumentException("Invoice number, lookup code and date are required");
        }
        try (PreparedStatement statement = connection.prepareStatement(SQL,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, supplierId);
            statement.setString(2, invoiceNo.trim());
            statement.setString(3, lookupCode.trim());
            statement.setObject(4, invoiceDate);
            statement.setInt(5, createdBy);
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
