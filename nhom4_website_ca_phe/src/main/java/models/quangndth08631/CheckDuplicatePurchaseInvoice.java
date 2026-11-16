package models.quangndth08631;

import java.sql.Connection;
import java.sql.SQLException;

/** Kiểm tra số hóa đơn đã tồn tại với cùng nhà cung cấp. */
public class CheckDuplicatePurchaseInvoice {

    private static final String SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM purchase_invoices
                WHERE supplier_id = ? AND invoice_no = ?
                                    AND (CAST(? AS bigint) IS NULL OR purchase_invoice_id <> ?)
            )
            """;

    public boolean exists(Connection connection, int supplierId, String invoiceNo,
                          Long excludePurchaseInvoiceId) throws SQLException {
        try (var statement = connection.prepareStatement(SQL)) {
            statement.setInt(1, supplierId);
            statement.setString(2, invoiceNo);
            statement.setObject(3, excludePurchaseInvoiceId);
            statement.setObject(4, excludePurchaseInvoiceId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }
}
