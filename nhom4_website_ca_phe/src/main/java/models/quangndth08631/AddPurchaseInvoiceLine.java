package models.quangndth08631;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Thêm dòng hàng vào một phiếu nhập nháp. */
public class AddPurchaseInvoiceLine {

    private static final String SQL = """
            INSERT INTO purchase_invoice_lines
                (purchase_invoice_id, line_no, package_id, supplier_item_code,
                 supplier_item_name, quantity, unit_cost)
            SELECT ?, ?, ?, ?, ?, ?, ?
            WHERE EXISTS (
                SELECT 1 FROM purchase_invoices
                WHERE purchase_invoice_id = ? AND status = 'DRAFT'
            )
            """;

    public long add(Connection connection, long invoiceId, int lineNo, int packageId,
                    String supplierItemCode, String supplierItemName, int quantity,
                    BigDecimal unitCost) throws SQLException {
        if (lineNo < 1 || quantity < 1 || unitCost == null || unitCost.signum() < 0) {
            throw new IllegalArgumentException("Invalid purchase line values");
        }
        try (PreparedStatement statement = connection.prepareStatement(SQL,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, invoiceId);
            statement.setInt(2, lineNo);
            statement.setInt(3, packageId);
            statement.setString(4, supplierItemCode);
            statement.setString(5, supplierItemName);
            statement.setInt(6, quantity);
            statement.setBigDecimal(7, unitCost);
            statement.setLong(8, invoiceId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Invoice not found or is not DRAFT");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Database did not return the purchase line id");
                }
                return keys.getLong(1);
            }
        }
    }
}
