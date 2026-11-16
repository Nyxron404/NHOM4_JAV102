package models.quangndth08631;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Xóa dòng hàng của phiếu nháp nếu dòng chưa phát sinh lô. */
public class DeletePurchaseInvoiceLine {

    private static final String SQL = """
            DELETE FROM purchase_invoice_lines pil
            USING purchase_invoices pi
            WHERE pil.purchase_line_id = ?
              AND pi.purchase_invoice_id = pil.purchase_invoice_id
              AND pi.status = 'DRAFT'
              AND NOT EXISTS (SELECT 1 FROM lots l WHERE l.purchase_line_id = pil.purchase_line_id)
            """;

    public boolean delete(Connection connection, long purchaseLineId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setLong(1, purchaseLineId);
            return statement.executeUpdate() == 1;
        }
    }
}
