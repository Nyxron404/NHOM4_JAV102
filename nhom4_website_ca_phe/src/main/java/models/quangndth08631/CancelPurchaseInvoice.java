package models.quangndth08631;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Hủy phiên nhập nháp; phiếu đã xác nhận không thể bị hủy theo luồng này. */
public class CancelPurchaseInvoice {

    private static final String SQL = """
            UPDATE purchase_invoices
            SET status = 'CANCELLED'
            WHERE purchase_invoice_id = ? AND status = 'DRAFT'
            """;

    public boolean cancel(Connection connection, long purchaseInvoiceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setLong(1, purchaseInvoiceId);
            return statement.executeUpdate() == 1;
        }
    }
}
